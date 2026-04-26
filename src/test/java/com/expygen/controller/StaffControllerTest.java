package com.expygen.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.expygen.admin.service.AuditService;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.SupportTicketRepository;
import com.expygen.repository.TicketReplyRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.PlanLimitService;

@ExtendWith(MockitoExtension.class)
class StaffControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private TicketReplyRepository ticketReplyRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private StaffController staffController;

    @Test
    void saveStaffCreatesCashierWithinOwnersShop() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        User formUser = User.builder()
                .name("Cashier One")
                .username("cashier@test.com")
                .phone("8888888888")
                .password("plain")
                .role(Role.OWNER)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(planLimitService.canAddUser(shop)).thenReturn(true);
        when(userRepository.findByUsername("cashier@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("8888888888")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain")).thenReturn("encoded-pass");

        String view = staffController.saveStaff(
                formUser,
                authentication,
                new RedirectAttributesModelMap(),
                null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("redirect:/staff", view);
        assertEquals(shop, savedUser.getShop());
        assertEquals(Role.CASHIER, savedUser.getRole());
        assertEquals("encoded-pass", savedUser.getPassword());
        assertTrue(savedUser.isActive());
        assertTrue(savedUser.isApproved());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveStaffRejectsDuplicateEmail() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        User existingUser = User.builder().id(99L).username("staff@test.com").phone("7777777777").build();
        User formUser = User.builder()
                .name("Staff")
                .username("staff@test.com")
                .phone("8888888888")
                .password("plain")
                .role(Role.CASHIER)
                .build();

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(planLimitService.canAddUser(shop)).thenReturn(true);
        when(userRepository.findByUsername("staff@test.com")).thenReturn(Optional.of(existingUser));

        String view = staffController.saveStaff(formUser, authentication, redirectAttributes, null);

        assertEquals("redirect:/staff", view);
        assertEquals("Email already exists", redirectAttributes.getFlashAttributes().get("error"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void toggleStatusFlipsActiveFlagAndRedirects() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        User staff = User.builder()
                .id(5L)
                .name("Cashier")
                .username("cashier@test.com")
                .phone("8888888888")
                .password("encoded")
                .role(Role.CASHIER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(userRepository.findById(5L)).thenReturn(Optional.of(staff));

        String view = staffController.toggleStatus(5L, redirectAttributes, authentication);

        assertEquals("redirect:/staff", view);
        assertFalse(staff.isActive());
        assertEquals("User cashier@test.com deactivated", redirectAttributes.getFlashAttributes().get("success"));
        verify(auditService).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteStaffPreventsOwnerFromDeletingSelf() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        String view = staffController.deleteStaff(owner.getId(), null, redirectAttributes, authentication);

        assertEquals("redirect:/staff", view);
        assertEquals("You cannot delete your own account! 🤦", redirectAttributes.getFlashAttributes().get("error"));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteStaffPreventsDeletingOwnerAccountFromShopPanel() {
        Shop shop = testShop();
        User actingOwner = testOwner(shop);
        User targetOwner = User.builder()
                .id(7L)
                .name("Second Owner")
                .username("second-owner@test.com")
                .phone("7777777777")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PRO)
                .build();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        when(authentication.getName()).thenReturn(actingOwner.getUsername());
        when(userRepository.findByUsername(actingOwner.getUsername())).thenReturn(Optional.of(actingOwner));
        when(userRepository.findById(7L)).thenReturn(Optional.of(targetOwner));

        String view = staffController.deleteStaff(7L, null, redirectAttributes, authentication);

        assertEquals("redirect:/staff", view);
        assertEquals("Owner account can only be deleted from admin portal",
                redirectAttributes.getFlashAttributes().get("error"));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void toggleStatusPreventsManagingOwnerAccountFromShopPanel() {
        Shop shop = testShop();
        User actingOwner = testOwner(shop);
        User targetOwner = User.builder()
                .id(7L)
                .name("Second Owner")
                .username("second-owner@test.com")
                .phone("7777777777")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PRO)
                .build();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        when(authentication.getName()).thenReturn(actingOwner.getUsername());
        when(userRepository.findByUsername(actingOwner.getUsername())).thenReturn(Optional.of(actingOwner));
        when(userRepository.findById(7L)).thenReturn(Optional.of(targetOwner));

        String view = staffController.toggleStatus(7L, redirectAttributes, authentication);

        assertEquals("redirect:/staff", view);
        assertEquals("Owner account can only be managed from admin portal",
                redirectAttributes.getFlashAttributes().get("error"));
        assertTrue(targetOwner.isActive());
        verify(userRepository, never()).save(targetOwner);
    }

    @Test
    void editStaffFormRedirectsWhenTryingToEditOwnerFromShopPanel() {
        Shop shop = testShop();
        User actingOwner = testOwner(shop);
        User targetOwner = User.builder()
                .id(7L)
                .name("Second Owner")
                .username("second-owner@test.com")
                .phone("7777777777")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PRO)
                .build();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        when(authentication.getName()).thenReturn(actingOwner.getUsername());
        when(userRepository.findByUsername(actingOwner.getUsername())).thenReturn(Optional.of(actingOwner));
        when(userRepository.findById(7L)).thenReturn(Optional.of(targetOwner));

        String view = staffController.editStaffForm(7L, new ExtendedModelMap(), authentication, redirectAttributes);

        assertEquals("redirect:/staff", view);
        assertEquals("Owner account can only be managed from admin portal",
                redirectAttributes.getFlashAttributes().get("error"));
    }

    @Test
    void activeStaffEndpointReturnsOnlyOtherActiveNonOwners() {
        Shop shop = testShop();
        User target = User.builder()
                .id(20L)
                .name("Delete Me")
                .username("delete@test.com")
                .phone("7777777777")
                .password("encoded")
                .role(Role.CASHIER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();
        User otherCashier = User.builder()
                .id(21L)
                .name("Other Cashier")
                .username("other@test.com")
                .phone("6666666666")
                .password("encoded")
                .role(Role.CASHIER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();
        User owner = testOwner(shop);

        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(userRepository.findByShop(shop)).thenReturn(List.of(target, otherCashier, owner));

        var response = staffController.getActiveStaff(20L);

        assertEquals(true, response.get("success"));
        List<?> activeStaff = (List<?>) response.get("activeStaff");
        assertEquals(1, activeStaff.size());
    }

    private Shop testShop() {
        return Shop.builder()
                .id(1L)
                .name("Core Shop")
                .planType(PlanType.PRO)
                .active(true)
                .build();
    }

    private User testOwner(Shop shop) {
        return User.builder()
                .id(1L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PRO)
                .build();
    }
}
