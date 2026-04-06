package com.hisaablite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
import com.hisaablite.entity.Product;
import com.hisaablite.entity.PurchaseBatch;
import com.hisaablite.entity.PurchaseEntry;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleStatus;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.Supplier;
import com.hisaablite.entity.SubscriptionPlan;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.PurchaseBatchRepository;
import com.hisaablite.repository.PurchaseEntryRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.SubscriptionPlanRepository;
import com.hisaablite.repository.SupplierRepository;
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

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseEntryRepository purchaseEntryRepository;

    @Autowired
    private PurchaseBatchRepository purchaseBatchRepository;

    private Long foreignSaleId;
    private Long localSupplierId;
    private Long localPurchaseId;

    @BeforeEach
    void setUp() {
        purchaseBatchRepository.deleteAll();
        purchaseEntryRepository.deleteAll();
        supplierRepository.deleteAll();
        productRepository.deleteAll();
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

        User owner = userRepository.save(User.builder()
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

        Product localProduct = productRepository.save(Product.builder()
                .name("Integration Medicine")
                .genericName("Integration Salt")
                .manufacturer("Integration Labs")
                .price(new BigDecimal("25.00"))
                .purchasePrice(new BigDecimal("18.00"))
                .mrp(new BigDecimal("28.00"))
                .stockQuantity(12)
                .gstPercent(12)
                .shop(shop)
                .active(true)
                .build());

        Supplier supplier = supplierRepository.save(Supplier.builder()
                .name("Integration Pharma")
                .contactPerson("Supplier Owner")
                .phone("9898989898")
                .gstNumber("29SUPPLIER1Z5")
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .shop(shop)
                .active(true)
                .build());
        localSupplierId = supplier.getId();

        PurchaseEntry localPurchase = purchaseEntryRepository.save(PurchaseEntry.builder()
                .purchaseDate(LocalDate.now())
                .createdAt(LocalDateTime.now().minusHours(4))
                .supplier(supplier)
                .supplierName(supplier.getName())
                .supplierInvoiceNumber("INT-INV-1")
                .totalAmount(new BigDecimal("240.00"))
                .shop(shop)
                .createdBy(owner)
                .build());
        localPurchaseId = localPurchase.getId();

        purchaseBatchRepository.save(PurchaseBatch.builder()
                .purchaseEntry(localPurchase)
                .shop(shop)
                .product(localProduct)
                .batchNumber("INT-BATCH-1")
                .expiryDate(LocalDate.now().plusDays(30))
                .receivedQuantity(12)
                .availableQuantity(12)
                .purchasePrice(new BigDecimal("18.00"))
                .salePrice(new BigDecimal("25.00"))
                .mrp(new BigDecimal("28.00"))
                .createdAt(LocalDateTime.now().minusHours(4))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Customer Information")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Scanner Ready")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadDashboardWithBatchVisibilityPanels() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("ultra-dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Batch & Expiry Signals")));
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
    void ownerCanLoadProductsPage() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Medicine Catalogue")));
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
    void ownerCanLoadPurchasesPage() throws Exception {
        mockMvc.perform(get("/purchases"))
                .andExpect(status().isOk())
                .andExpect(view().name("purchases"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Receive medicines with batch and expiry control")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadPurchaseReturnsPage() throws Exception {
        mockMvc.perform(get("/purchases/returns"))
                .andExpect(status().isOk())
                .andExpect(view().name("purchase-returns"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Return unsold batch stock back to supplier safely")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadStockAdjustmentsPage() throws Exception {
        mockMvc.perform(get("/purchases/adjustments"))
                .andExpect(status().isOk())
                .andExpect(view().name("stock-adjustments"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Correct batch or opening stock with an audit trail")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadExpiryReportPage() throws Exception {
        mockMvc.perform(get("/purchases/expiry-report"))
                .andExpect(status().isOk())
                .andExpect(view().name("expiry-report"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Track expired and near-expiry batches before they hit the counter")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadSuppliersPage() throws Exception {
        mockMvc.perform(get("/purchases/suppliers"))
                .andExpect(status().isOk())
                .andExpect(view().name("suppliers"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Supplier Directory")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadSupplierDetailPage() throws Exception {
        mockMvc.perform(get("/purchases/suppliers/" + localSupplierId))
                .andExpect(status().isOk())
                .andExpect(view().name("supplier-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Supplier Expiry Watch")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void ownerCanLoadPurchaseDetailPage() throws Exception {
        mockMvc.perform(get("/purchases/view/" + localPurchaseId))
                .andExpect(status().isOk())
                .andExpect(view().name("purchase-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Batch Lines")));
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
