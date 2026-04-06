package com.hisaablite.service;

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
import com.hisaablite.admin.repository.AuditLogRepository;
import com.hisaablite.admin.service.AuditService;
import com.hisaablite.dto.PurchaseReturnForm;
import com.hisaablite.dto.PurchaseReturnLineForm;
import com.hisaablite.dto.StockAdjustmentForm;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.PurchaseBatch;
import com.hisaablite.entity.PurchaseEntry;
import com.hisaablite.entity.PurchaseReturn;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.StockAdjustment;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.PurchaseBatchRepository;
import com.hisaablite.repository.PurchaseEntryRepository;
import com.hisaablite.repository.PurchaseReturnRepository;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.StockAdjustmentRepository;
import com.hisaablite.repository.UserRepository;

@DataJpaTest
@Import({InventoryControlService.class, AuditService.class, InventoryControlServiceTest.TestBeans.class})
@ActiveProfiles("test")
class InventoryControlServiceTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private InventoryControlService inventoryControlService;

    @Autowired
    private PurchaseBatchRepository purchaseBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseEntryRepository purchaseEntryRepository;

    @Autowired
    private PurchaseReturnRepository purchaseReturnRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void recordPurchaseReturnReducesBatchAndProductStock() {
        Shop shop = createShop("RET1");
        User owner = createOwner(shop, "RET1");
        Product product = createProduct(shop, "Amoxicillin", 10);
        PurchaseBatch batch = createBatch(product, shop, owner, "RET-B1", 10, "9.50");

        PurchaseReturnForm form = new PurchaseReturnForm();
        form.setReturnDate(LocalDate.now());
        PurchaseReturnLineForm line = new PurchaseReturnLineForm();
        line.setPurchaseBatchId(batch.getId());
        line.setQuantity(4);
        line.setReason("Supplier recall");
        form.setItems(List.of(line));

        PurchaseReturn purchaseReturn = inventoryControlService.recordPurchaseReturn(form, shop, owner);

        assertNotNull(purchaseReturn.getId());
        assertEquals(1, purchaseReturn.getLines().size());
        assertEquals(6, purchaseBatchRepository.findById(batch.getId()).orElseThrow().getAvailableQuantity());
        assertEquals(6, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, purchaseReturn.getTotalAmount().compareTo(new BigDecimal("38.00")));
        assertEquals(1, purchaseReturnRepository.countByShop(shop));
        assertEquals(1, auditLogRepository.findByActionOrderByTimestampDesc("PURCHASE_RETURN_RECORDED").size());
    }

    @Test
    void recordPurchaseReturnRejectsQuantityAboveAvailableBatchStock() {
        Shop shop = createShop("RET2");
        User owner = createOwner(shop, "RET2");
        Product product = createProduct(shop, "Cetrizine", 5);
        PurchaseBatch batch = createBatch(product, shop, owner, "RET-B2", 5, "4.00");

        PurchaseReturnForm form = new PurchaseReturnForm();
        form.setReturnDate(LocalDate.now());
        PurchaseReturnLineForm line = new PurchaseReturnLineForm();
        line.setPurchaseBatchId(batch.getId());
        line.setQuantity(6);
        line.setReason("Damaged strip");
        form.setItems(List.of(line));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> inventoryControlService.recordPurchaseReturn(form, shop, owner));

        assertEquals("Cannot return more than available stock for batch RET-B2", exception.getMessage());
    }

    @Test
    void recordStockAdjustmentUpdatesBatchAndProductStock() {
        Shop shop = createShop("ADJ1");
        User owner = createOwner(shop, "ADJ1");
        Product product = createProduct(shop, "Pantoprazole", 8);
        PurchaseBatch batch = createBatch(product, shop, owner, "ADJ-B1", 8, "12.00");

        StockAdjustmentForm form = new StockAdjustmentForm();
        form.setAdjustmentDate(LocalDate.now());
        form.setPurchaseBatchId(batch.getId());
        form.setQuantityDelta(-3);
        form.setReason("Count mismatch");
        form.setNotes("Shelf count lower than expected");

        StockAdjustment adjustment = inventoryControlService.recordStockAdjustment(form, shop, owner);

        assertNotNull(adjustment.getId());
        assertEquals(5, purchaseBatchRepository.findById(batch.getId()).orElseThrow().getAvailableQuantity());
        assertEquals(5, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(1, stockAdjustmentRepository.countByShop(shop));
        assertEquals(1, auditLogRepository.findByActionOrderByTimestampDesc("STOCK_ADJUSTED").size());
    }

    @Test
    void recordManualStockAdjustmentRejectsReductionBeyondManualOpeningStock() {
        Shop shop = createShop("ADJ2");
        User owner = createOwner(shop, "ADJ2");
        Product product = createProduct(shop, "Vitamin C", 10);
        createBatch(product, shop, owner, "ADJ-B2", 8, "5.00");

        StockAdjustmentForm form = new StockAdjustmentForm();
        form.setAdjustmentDate(LocalDate.now());
        form.setProductId(product.getId());
        form.setQuantityDelta(-3);
        form.setReason("Manual correction");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> inventoryControlService.recordStockAdjustment(form, shop, owner));

        assertEquals("Negative manual adjustment exceeds opening/manual stock for Vitamin C", exception.getMessage());
    }

    private Shop createShop(String suffix) {
        return shopRepository.save(Shop.builder()
                .name("Inventory Shop " + suffix)
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
                .username("inventory-owner" + suffix + "@example.com")
                .phone("90200000" + suffix)
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

    private Product createProduct(Shop shop, String name, int stockQuantity) {
        return productRepository.save(Product.builder()
                .name(name)
                .price(new BigDecimal("15.00"))
                .stockQuantity(stockQuantity)
                .gstPercent(12)
                .shop(shop)
                .active(true)
                .build());
    }

    private PurchaseBatch createBatch(Product product, Shop shop, User owner, String batchNumber, int quantity, String purchasePrice) {
        PurchaseEntry entry = purchaseEntryRepository.save(PurchaseEntry.builder()
                .purchaseDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .supplierName("Supplier " + batchNumber)
                .supplierInvoiceNumber("INV-" + batchNumber)
                .shop(shop)
                .createdBy(owner)
                .totalAmount(new BigDecimal(purchasePrice).multiply(BigDecimal.valueOf(quantity)))
                .build());

        return purchaseBatchRepository.save(PurchaseBatch.builder()
                .purchaseEntry(entry)
                .shop(shop)
                .product(product)
                .batchNumber(batchNumber)
                .expiryDate(LocalDate.now().plusMonths(8))
                .receivedQuantity(quantity)
                .availableQuantity(quantity)
                .purchasePrice(new BigDecimal(purchasePrice))
                .salePrice(product.getPrice())
                .mrp(product.getPrice())
                .createdAt(LocalDateTime.now())
                .build());
    }
}
