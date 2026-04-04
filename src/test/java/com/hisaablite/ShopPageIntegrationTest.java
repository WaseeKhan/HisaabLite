package com.hisaablite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleStatus;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.SubscriptionPlan;
import com.hisaablite.entity.User;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.SubscriptionPlanRepository;
import com.hisaablite.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SaleRepository saleRepository;

    private Long foreignSaleId;

    @BeforeEach
    void setUp() {
        saleRepository.deleteAll();
        userRepository.deleteAll();
        shopRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .planName("PREMIUM")
                .price(1999.0)
                .durationInDays(30)
                .description("Premium test plan")
                .features("Billing,Products,Staff")
                .maxUsers(10)
                .maxProducts(500)
                .active(true)
                .build());

        Shop shop = shopRepository.save(Shop.builder()
                .name("Integration Shop")
                .panNumber("ABCDE1234F")
                .gstNumber("29ABCDE1234F1Z5")
                .address("Main Road")
                .city("Bengaluru")
                .state("Karnataka")
                .pincode("560001")
                .planType(PlanType.PREMIUM)
                .active(true)
                .subscriptionStartDate(LocalDateTime.now().minusDays(10))
                .subscriptionEndDate(LocalDateTime.now().plusDays(20))
                .build());

        userRepository.save(User.builder()
                .name("Owner User")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .createdAt(LocalDateTime.now().minusDays(5))
                .approvalDate(LocalDateTime.now().minusDays(5))
                .subscriptionStartDate(LocalDateTime.now().minusDays(10))
                .subscriptionEndDate(LocalDateTime.now().plusDays(20))
                .build());

        userRepository.save(User.builder()
                .name("Cashier User")
                .username("cashier@test.com")
                .phone("8888888888")
                .password("encoded")
                .role(Role.CASHIER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .createdAt(LocalDateTime.now().minusDays(3))
                .approvalDate(LocalDateTime.now().minusDays(3))
                .subscriptionStartDate(LocalDateTime.now().minusDays(10))
                .subscriptionEndDate(LocalDateTime.now().plusDays(20))
                .build());

        Shop otherShop = shopRepository.save(Shop.builder()
                .name("Second Shop")
                .panNumber("OTHER1234P")
                .planType(PlanType.PREMIUM)
                .active(true)
                .subscriptionStartDate(LocalDateTime.now().minusDays(5))
                .subscriptionEndDate(LocalDateTime.now().plusDays(25))
                .build());

        User otherOwner = userRepository.save(User.builder()
                .name("Other Owner")
                .username("other-owner@test.com")
                .phone("7777777777")
                .password("encoded")
                .role(Role.OWNER)
                .shop(otherShop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .createdAt(LocalDateTime.now().minusDays(4))
                .approvalDate(LocalDateTime.now().minusDays(4))
                .subscriptionStartDate(LocalDateTime.now().minusDays(5))
                .subscriptionEndDate(LocalDateTime.now().plusDays(25))
                .build());

        foreignSaleId = saleRepository.save(Sale.builder()
                .shop(otherShop)
                .createdBy(otherOwner)
                .saleDate(LocalDateTime.now().minusHours(2))
                .totalAmount(new BigDecimal("150.00"))
                .status(SaleStatus.COMPLETED)
                .customerName("Hidden Customer")
                .customerPhone("9123456789")
                .build()).getId();
    }

    @Test
    void unauthenticatedBillingPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/sales/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void unknownPageUsesStyled404Page() throws Exception {
        mockMvc.perform(get("/page-that-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page Not Found")));
    }

    @Test
    void invalidResetPasswordTokenUsesStyled404Page() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", "invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("invalid or has expired")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadBillingPage() throws Exception {
        mockMvc.perform(get("/sales/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("sale-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Customer Information")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadSalesHistoryPage() throws Exception {
        mockMvc.perform(get("/sales/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("sales-history"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sales History")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadStaffPage() throws Exception {
        mockMvc.perform(get("/staff"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff-list"));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadSupportPage() throws Exception {
        mockMvc.perform(get("/support"))
                .andExpect(status().isOk())
                .andExpect(view().name("support"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Support")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadProfilePage() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"));
    }

    @Test
    void unauthenticatedInvoicePdfRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/sales/invoice/" + foreignSaleId + "/pdf"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void unauthenticatedSupportCreateRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/support/create")
                        .param("subject", "Help")
                        .param("message", "Need help")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCannotAccessAnotherShopsInvoice() throws Exception {
        mockMvc.perform(get("/sales/invoice/" + foreignSaleId))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}
