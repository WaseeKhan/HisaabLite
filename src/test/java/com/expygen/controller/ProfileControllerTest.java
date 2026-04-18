package com.expygen.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.expygen.dto.ShopProfileUpdateRequest;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.EvolutionApiService;
import com.expygen.service.ShopService;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ShopService shopService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EvolutionApiService evolutionApiService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProfileController profileController;

    @Test
    void profilePageLoadsOwnerProfileData() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Model model = new ExtendedModelMap();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(userRepository.countByShop(shop)).thenReturn(4L);
        when(productRepository.countByShop(shop)).thenReturn(12L);
        when(saleRepository.countByShop(shop)).thenReturn(25L);

        String view = profileController.profilePage(authentication, model);

        assertEquals("profile", view);
        assertSame(shop, model.getAttribute("shop"));
        assertSame(owner, model.getAttribute("user"));
        assertEquals("PREMIUM", model.getAttribute("planType"));
        assertEquals("profile", model.getAttribute("currentPage"));
        assertEquals(4L, model.getAttribute("currentStaffCount"));
        assertEquals(12L, model.getAttribute("currentProductCount"));
        assertEquals(25L, model.getAttribute("totalInvoices"));
    }

    @Test
    void updateProfileDelegatesToShopServiceAndRedirects() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        ShopProfileUpdateRequest request = new ShopProfileUpdateRequest();
        request.setAddress("New Address");

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));

        String view = profileController.updateProfile(request, authentication);

        assertEquals("redirect:/dashboard", view);
        verify(shopService).updateProfile(owner, request);
    }

    private Shop testShop() {
        return Shop.builder()
                .id(11L)
                .name("Reliable Store")
                .planType(PlanType.PREMIUM)
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(30))
                .subscriptionStartDate(LocalDateTime.now().minusDays(15))
                .subscriptionEndDate(LocalDateTime.now().plusDays(15))
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
                .currentPlan(PlanType.PREMIUM)
                .build();
    }
}
