package com.expygen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.expygen.dto.ExpiryReportBucket;
import com.expygen.dto.ExpiryReportItem;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.UserRepository;

@DataJpaTest
@Import(ExpiryAlertService.class)
@ActiveProfiles("test")
class ExpiryAlertServiceTest {

    @Autowired
    private ExpiryAlertService expiryAlertService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseEntryRepository purchaseEntryRepository;

    @Autowired
    private PurchaseBatchRepository purchaseBatchRepository;

    @Test
    void buildAlertSummaryCountsExpiredAndUpcomingBuckets() {
        Shop shop = createShop("ER1");
        User owner = createOwner(shop, "ER1");
        Product product = createProduct(shop, "Cefixime");

        createBatch(shop, owner, product, "EXP-1", LocalDate.now().minusDays(2), 4);
        createBatch(shop, owner, product, "EXP-2", LocalDate.now().plusDays(3), 6);
        createBatch(shop, owner, product, "EXP-3", LocalDate.now().plusDays(20), 8);
        createBatch(shop, owner, product, "EXP-4", LocalDate.now().plusDays(55), 10);
        createBatch(shop, owner, product, "EXP-5", LocalDate.now().plusDays(95), 12);

        var summary = expiryAlertService.buildAlertSummary(shop);

        assertEquals(2, summary.getCriticalAlertCount());
        assertEquals(10, summary.getCriticalAlertUnits());
        assertEquals(1, summary.getExpiredBatchCount());
        assertEquals(4, summary.getExpiredUnits());
        assertEquals(1, summary.getExpiringIn7DaysCount());
        assertEquals(6, summary.getExpiringIn7DaysUnits());
        assertEquals(2, summary.getExpiringIn30DaysCount());
        assertEquals(14, summary.getExpiringIn30DaysUnits());
        assertEquals(3, summary.getExpiringIn60DaysCount());
        assertEquals(24, summary.getExpiringIn60DaysUnits());
        assertEquals(3, summary.getExpiringIn90DaysCount());
        assertEquals(24, summary.getExpiringIn90DaysUnits());
    }

    @Test
    void buildReportItemsReturnsCriticalBatchesInExpiryOrder() {
        Shop shop = createShop("ER2");
        User owner = createOwner(shop, "ER2");
        Product product = createProduct(shop, "Azithromycin");

        createBatch(shop, owner, product, "CRIT-OLD", LocalDate.now().minusDays(1), 2);
        createBatch(shop, owner, product, "CRIT-SOON", LocalDate.now().plusDays(2), 5);
        createBatch(shop, owner, product, "SAFE-30", LocalDate.now().plusDays(25), 7);

        List<ExpiryReportItem> criticalItems = expiryAlertService.buildReportItems(shop, ExpiryReportBucket.CRITICAL, 10);

        assertEquals(2, criticalItems.size());
        assertEquals("CRIT-OLD", criticalItems.get(0).getBatchNumber());
        assertEquals("Expired", criticalItems.get(0).getStatusLabel());
        assertEquals("expired", criticalItems.get(0).getAlertLevel());
        assertEquals("CRIT-SOON", criticalItems.get(1).getBatchNumber());
        assertFalse(criticalItems.stream().anyMatch(item -> "SAFE-30".equals(item.getBatchNumber())));
    }

    private Shop createShop(String suffix) {
        return shopRepository.save(Shop.builder()
                .name("Expiry Shop " + suffix)
                .panNumber("PAN" + suffix + "9988")
                .city("Pune")
                .state("MH")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build());
    }

    private User createOwner(Shop shop, String suffix) {
        return userRepository.save(User.builder()
                .name("Expiry Owner " + suffix)
                .username("expiry-owner-" + suffix + "@example.com")
                .phone("9001000" + suffix)
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
                .price(new BigDecimal("120.00"))
                .stockQuantity(0)
                .gstPercent(12)
                .shop(shop)
                .active(true)
                .build());
    }

    private PurchaseBatch createBatch(Shop shop, User owner, Product product, String batchNumber, LocalDate expiryDate, int quantity) {
        PurchaseEntry purchaseEntry = purchaseEntryRepository.save(PurchaseEntry.builder()
                .purchaseDate(LocalDate.now())
                .supplierName("Health Distributor")
                .supplierInvoiceNumber("INV-" + batchNumber)
                .totalAmount(new BigDecimal("500.00"))
                .shop(shop)
                .createdBy(owner)
                .createdAt(LocalDateTime.now())
                .build());

        return purchaseBatchRepository.save(PurchaseBatch.builder()
                .batchNumber(batchNumber)
                .expiryDate(expiryDate)
                .receivedQuantity(quantity)
                .availableQuantity(quantity)
                .purchasePrice(new BigDecimal("50.00"))
                .salePrice(new BigDecimal("75.00"))
                .mrp(new BigDecimal("80.00"))
                .createdAt(LocalDateTime.now())
                .purchaseEntry(purchaseEntry)
                .product(product)
                .shop(shop)
                .active(true)
                .build());
    }
}
