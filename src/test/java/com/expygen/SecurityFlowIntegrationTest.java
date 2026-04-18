package com.expygen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.expygen.admin.repository.AuditLogRepository;
import com.expygen.entity.AuditLog;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.SupplierRepository;
import com.expygen.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepository;

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

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        purchaseBatchRepository.deleteAll();
        purchaseEntryRepository.deleteAll();
        supplierRepository.deleteAll();
        productRepository.deleteAll();
        saleRepository.deleteAll();
        userRepository.deleteAll();
        shopRepository.deleteAll();

        Shop shop = shopRepository.save(Shop.builder()
                .name("Auth Test Shop")
                .planType(PlanType.PREMIUM)
                .build());

        userRepository.save(User.builder()
                .name("Owner Test")
                .username("owner@test.com")
                .phone("9999999992")
                .password(passwordEncoder.encode("password123"))
                .role(Role.OWNER)
                .approved(true)
                .active(true)
                .shop(shop)
                .currentPlan(PlanType.PREMIUM)
                .build());

        userRepository.save(User.builder()
                .name("Admin Test")
                .username("admin@test.com")
                .phone("9999999993")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .approved(true)
                .active(true)
                .shop(shop)
                .currentPlan(PlanType.PREMIUM)
                .build());
    }

    @Test
    void unauthenticatedDashboardRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void shopLoginPageShowsLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Logged out successfully")));
    }

    @Test
    void adminLoginPageShowsLogoutMessage() throws Exception {
        mockMvc.perform(get("/admin/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Logged out successfully")));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void legacyOwnerDashboardRedirectsToCanonicalDashboard() throws Exception {
        mockMvc.perform(get("/owner/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "manager@test.com", roles = "MANAGER")
    void legacyManagerDashboardRedirectsToCanonicalDashboard() throws Exception {
        mockMvc.perform(get("/manager/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "cashier@test.com", roles = "CASHIER")
    void legacyCashierDashboardRedirectsToCanonicalDashboard() throws Exception {
        mockMvc.perform(get("/cashier/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "OWNER")
    void shopLogoutRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> "LOGOUT".equals(log.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getUsername()).isEqualTo("owner@test.com");
        assertThat(auditLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminLogoutRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(post("/admin/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?logout=true"));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> "ADMIN_LOGOUT".equals(log.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getUsername()).isEqualTo("admin@test.com");
        assertThat(auditLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shopLoginSuccessCreatesAuditLog() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "owner@test.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> "LOGIN_SUCCESS".equals(log.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getUsername()).isEqualTo("owner@test.com");
        assertThat(auditLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shopLoginFailureCreatesAuditLog() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "owner@test.com")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=bad"));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> "LOGIN_FAILED".equals(log.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getUsername()).isEqualTo("owner@test.com");
        assertThat(auditLog.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void adminLoginSuccessCreatesAuditLog() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .param("username", "admin@test.com")
                        .param("password", "admin123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> "LOGIN_SUCCESS".equals(log.getAction()) && "admin@test.com".equals(log.getUsername()))
                .findFirst()
                .orElse(null);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void adminLoginFailureCreatesAuditLog() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .param("username", "admin@test.com")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=true"));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> "LOGIN_FAILED".equals(log.getAction()) && "admin@test.com".equals(log.getUsername()))
                .findFirst()
                .orElse(null);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getStatus()).isEqualTo("FAILED");
    }
}
