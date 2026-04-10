package com.expygen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.expygen.admin.repository.AuditLogRepository;
import com.expygen.admin.service.AuditService;
import com.expygen.dto.PurchaseEntryForm;
import com.expygen.dto.PurchaseLineForm;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.SupplierRepository;
import com.expygen.repository.UserRepository;

@DataJpaTest
@Import({PurchaseService.class, SupplierService.class, AuditService.class, PurchaseServiceTest.TestBeans.class})
@ActiveProfiles("test")
class PurchaseServiceTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private PurchaseEntryRepository purchaseEntryRepository;

    @Autowired
    private PurchaseBatchRepository purchaseBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void recordPurchaseCreatesBatchEntriesAndUpdatesProductStock() {
        Shop shop = createShop("P1");
        User owner = createOwner(shop, "P1");
        Product product = createProduct(shop, "Paracetamol 650");

        PurchaseEntryForm form = new PurchaseEntryForm();
        form.setPurchaseDate(LocalDate.now());
        form.setSupplierName("ABC Pharma");
        form.setSupplierInvoiceNumber("INV-001");

        PurchaseLineForm line = new PurchaseLineForm();
        line.setProductId(product.getId());
        line.setBatchNumber("PARA-01");
        line.setExpiryDate(LocalDate.now().plusMonths(10));
        line.setQuantity(20);
        line.setPurchasePrice(new BigDecimal("8.50"));
        line.setSalePrice(new BigDecimal("12.00"));
        line.setMrp(new BigDecimal("14.00"));
        form.setItems(List.of(line));

        PurchaseEntry savedEntry = purchaseService.recordPurchase(form, shop, owner);

        assertNotNull(savedEntry.getId());
        assertEquals(1, purchaseBatchRepository.findByPurchaseEntryOrderByExpiryDateAscIdAsc(savedEntry).size());
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(20, updatedProduct.getStockQuantity());
        assertEquals(0, new BigDecimal("170.00").compareTo(savedEntry.getTotalAmount()));
        assertEquals(1, purchaseEntryRepository.countByShop(shop));
        assertNotNull(savedEntry.getSupplier());
        Supplier supplier = supplierRepository.findById(savedEntry.getSupplier().getId()).orElseThrow();
        assertEquals("ABC Pharma", supplier.getName());
        assertEquals(1, supplierRepository.countByShopAndActiveTrue(shop));
        assertEquals(1, auditLogRepository.findByActionOrderByTimestampDesc("PURCHASE_RECORDED").size());
    }

    @Test
    void recordPurchaseRejectsMissingSupplierName() {
        Shop shop = createShop("P2");
        User owner = createOwner(shop, "P2");

        PurchaseEntryForm form = new PurchaseEntryForm();
        form.setPurchaseDate(LocalDate.now());
        form.setSupplierName(" ");
        form.setItems(List.of(new PurchaseLineForm()));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> purchaseService.recordPurchase(form, shop, owner));

        assertEquals("Supplier name is required", exception.getMessage());
    }

    private Shop createShop(String suffix) {
        return shopRepository.save(Shop.builder()
                .name("Purchase Shop " + suffix)
                .panNumber("PAN" + suffix + "12345")
                .city("Mumbai")
                .state("MH")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build());
    }

    private User createOwner(Shop shop, String suffix) {
        return userRepository.save(User.builder()
                .name("Owner " + suffix)
                .username("purchase-owner" + suffix + "@example.com")
                .phone("90100000" + suffix)
                .password("encoded-password")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .createdAt(LocalDateTime.now())
                .approvalDate(LocalDateTime.now())
                .build());
    }

    private Product createProduct(Shop shop, String name) {
        return productRepository.save(Product.builder()
                .name(name)
                .price(new BigDecimal("10.00"))
                .stockQuantity(0)
                .gstPercent(12)
                .shop(shop)
                .active(true)
                .build());
    }
}
