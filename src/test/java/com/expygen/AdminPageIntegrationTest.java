package com.expygen;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.PurchaseReturnLineRepository;
import com.expygen.repository.PurchaseReturnRepository;
import com.expygen.repository.SaleItemBatchAllocationRepository;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.StockAdjustmentRepository;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.SupplierRepository;
import com.expygen.repository.SupportTicketRepository;
import com.expygen.repository.TicketReplyRepository;
import com.expygen.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private PurchaseEntryRepository purchaseEntryRepository;

    @Autowired
    private PurchaseBatchRepository purchaseBatchRepository;

    @Autowired
    private PurchaseReturnLineRepository purchaseReturnLineRepository;

    @Autowired
    private PurchaseReturnRepository purchaseReturnRepository;

    @Autowired
    private SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private TicketReplyRepository ticketReplyRepository;

    @BeforeEach
    void setUp() {
        ticketReplyRepository.deleteAll();
        supportTicketRepository.deleteAll();
        stockAdjustmentRepository.deleteAll();
        saleItemBatchAllocationRepository.deleteAll();
        saleItemRepository.deleteAll();
        saleRepository.deleteAll();
        purchaseReturnLineRepository.deleteAll();
        purchaseReturnRepository.deleteAll();
        purchaseBatchRepository.deleteAll();
        purchaseEntryRepository.deleteAll();
        supplierRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        shopRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .planName("PREMIUM")
                .price(1999.0)
                .durationInDays(30)
                .description("Premium admin test plan")
                .features("Billing,Products,Staff")
                .maxUsers(10)
                .maxProducts(500)
                .active(true)
                .build());

        Shop shop = shopRepository.save(Shop.builder()
                .name("Admin Integration Shop")
                .panNumber("ADMIN1234P")
                .gstNumber("29ADMIN1234P1Z5")
                .address("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .pincode("560001")
                .planType(PlanType.PREMIUM)
                .active(true)
                .subscriptionStartDate(LocalDateTime.now().minusDays(5))
                .subscriptionEndDate(LocalDateTime.now().plusDays(25))
                .build());

        userRepository.save(User.builder()
                .name("Admin User")
                .username("admin@test.com")
                .phone("9999999991")
                .password("encoded")
                .role(Role.ADMIN)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .createdAt(LocalDateTime.now().minusDays(5))
                .approvalDate(LocalDateTime.now().minusDays(5))
                .build());

        userRepository.save(User.builder()
                .name("Owner User")
                .username("owner@test.com")
                .phone("9999999992")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .createdAt(LocalDateTime.now().minusDays(5))
                .approvalDate(LocalDateTime.now().minusDays(5))
                .build());
    }

    @Test
    void unauthenticatedAdminDashboardRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanLoadDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Admin Dashboard")));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanLoadUsersPage() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("User Management")));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanLoadShopsPage() throws Exception {
        mockMvc.perform(get("/admin/shops"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/shops"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shop Management")));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanLoadSubscriptionsPage() throws Exception {
        mockMvc.perform(get("/admin/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/subscriptions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Subscription Plans")));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanLoadSupportDashboard() throws Exception {
        mockMvc.perform(get("/admin/support"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/support-dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Support Dashboard")));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanLoadAuditPage() throws Exception {
        mockMvc.perform(get("/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/audit-logs"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Audit Logs")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void nonAdminCannotLoadAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/admin/access-denied"));
    }
}
