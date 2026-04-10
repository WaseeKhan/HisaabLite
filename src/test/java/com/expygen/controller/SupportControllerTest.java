package com.expygen.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.SupportTicket;
import com.expygen.entity.TicketPriority;
import com.expygen.entity.User;
import com.expygen.repository.SupportTicketRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.SupportService;

@ExtendWith(MockitoExtension.class)
class SupportControllerTest {

    @Mock
    private SupportService supportService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SupportController supportController;

    @Test
    void supportPageLoadsSafelyWithoutAuthentication() {
        Model model = new ExtendedModelMap();

        String view = supportController.supportPage(model, null, 0);

        assertEquals("support", view);
        assertEquals("FREE", model.getAttribute("planType"));
        assertEquals("support", model.getAttribute("currentPage"));
    }

    @Test
    void ownerCanCreateTicketAndIsRedirectedToTicketPage() {
        Shop shop = Shop.builder()
                .id(5L)
                .name("Support Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build();
        User owner = User.builder()
                .id(9L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber("HL202604040001");

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(supportService.createTicket(owner, "Need help", "Billing issue", TicketPriority.HIGH)).thenReturn(ticket);

        String view = supportController.createTicket(
                "Need help",
                "Billing issue",
                "HIGH",
                authentication,
                redirectAttributes);

        assertEquals("redirect:/support/ticket/" + ticket.getTicketNumber(), view);
        assertEquals("Ticket created successfully! Ticket #: " + ticket.getTicketNumber(),
                redirectAttributes.getFlashAttributes().get("success"));
    }

    @Test
    void supportPageLoadsShopTicketsForOwner() {
        Shop shop = Shop.builder()
                .id(6L)
                .name("Support Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.BASIC)
                .active(true)
                .build();
        User owner = User.builder()
                .id(3L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();
        Model model = new ExtendedModelMap();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(supportService.getShopTickets(owner, 0)).thenReturn(Page.empty());
        when(supportTicketRepository.countByShop(shop)).thenReturn(0L);
        when(supportTicketRepository.countByShopAndStatus(shop, com.expygen.entity.TicketStatus.OPEN)).thenReturn(0L);
        when(supportTicketRepository.countByShopAndStatus(shop, com.expygen.entity.TicketStatus.IN_PROGRESS)).thenReturn(0L);
        when(supportTicketRepository.countByShopAndPriority(shop, TicketPriority.URGENT)).thenReturn(0L);

        String view = supportController.supportPage(model, authentication, 0);

        assertEquals("support", view);
        assertSame(owner, model.getAttribute("user"));
        assertSame(shop, model.getAttribute("shop"));
        assertEquals("BASIC", model.getAttribute("planType"));
    }

    @Test
    void replyToTicketRedirectsBackToTicketAndCallsService() {
        Shop shop = Shop.builder()
                .id(6L)
                .name("Support Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.BASIC)
                .active(true)
                .build();
        User owner = User.builder()
                .id(3L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();
        SupportTicket ticket = new SupportTicket();
        ticket.setId(77L);
        ticket.setTicketNumber("HL202604050001");

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(supportService.getTicket(ticket.getTicketNumber(), owner, false)).thenReturn(ticket);

        String view = supportController.addReply(
                ticket.getTicketNumber(),
                "We need update",
                authentication,
                redirectAttributes);

        assertEquals("redirect:/support/ticket/" + ticket.getTicketNumber(), view);
        assertEquals("Reply added successfully", redirectAttributes.getFlashAttributes().get("success"));
        verify(supportService).addReply(ticket.getId(), owner, "We need update", false);
    }

    @Test
    void resolveTicketRedirectsBackToTicketAndCallsService() {
        Shop shop = Shop.builder()
                .id(7L)
                .name("Support Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build();
        User owner = User.builder()
                .id(4L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();
        SupportTicket ticket = new SupportTicket();
        ticket.setId(88L);
        ticket.setTicketNumber("HL202604050002");

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(supportService.getTicket(ticket.getTicketNumber(), owner, false)).thenReturn(ticket);

        String view = supportController.resolveTicket(
                ticket.getTicketNumber(),
                authentication,
                redirectAttributes);

        assertEquals("redirect:/support/ticket/" + ticket.getTicketNumber(), view);
        assertEquals("Ticket marked as resolved", redirectAttributes.getFlashAttributes().get("success"));
        verify(supportService).resolveTicket(ticket.getId(), owner, false);
    }

    @Test
    void nonAdminCannotCloseTicket() {
        Shop shop = Shop.builder()
                .id(8L)
                .name("Support Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.BASIC)
                .active(true)
                .build();
        User owner = User.builder()
                .id(5L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .build();

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));

        String view = supportController.closeTicket("HL202604050003", authentication, redirectAttributes);

        assertEquals("redirect:/support/ticket/HL202604050003", view);
        assertEquals("Only admin can close tickets", redirectAttributes.getFlashAttributes().get("error"));
    }
}
