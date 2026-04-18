package com.expygen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.expygen.admin.repository.AuditLogRepository;
import com.expygen.admin.service.AuditService;
import com.expygen.dto.CartItem;
import com.expygen.entity.AuditLog;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.Role;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleItemBatchAllocation;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleItemBatchAllocationRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.UserRepository;

@DataJpaTest
@Import({SaleService.class, AuditService.class, SaleServiceTest.TestBeans.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleServiceTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseEntryRepository purchaseEntryRepository;

    @Autowired
    private PurchaseBatchRepository purchaseBatchRepository;

    @Autowired
    private SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void completeSalePersistsSaleAndDeductsStock() {
        Shop shop = createShop("A");
        User owner = createOwner(shop, "A");
        Product tea = createProduct(shop, "Tea", "10.00", 10, 18);
        Product sugar = createProduct(shop, "Sugar", "20.00", 5, 5);

        List<CartItem> cartItems = List.of(
                CartItem.builder().productId(tea.getId()).quantity(2).build(),
                CartItem.builder().productId(sugar.getId()).quantity(1).build());

        Sale savedSale = saleService.completeSale(cartItems, shop, owner);

        assertNotNull(savedSale.getId());
        assertEquals(SaleStatus.COMPLETED, savedSale.getStatus());
        assertEquals(0, new BigDecimal("44.60").compareTo(savedSale.getTotalAmount()));
        assertEquals(0, new BigDecimal("4.60").compareTo(savedSale.getTotalGstAmount()));
        assertEquals(0, new BigDecimal("40.00").compareTo(savedSale.getTaxableAmount()));

        Product updatedTea = productRepository.findById(tea.getId()).orElseThrow();
        Product updatedSugar = productRepository.findById(sugar.getId()).orElseThrow();
        assertEquals(8, updatedTea.getStockQuantity());
        assertEquals(4, updatedSugar.getStockQuantity());

        List<SaleItem> items = saleItemRepository.findBySale(savedSale);
        assertEquals(2, items.size());
        assertEquals(1L, saleRepository.countByCreatedBy(owner));
        List<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc("SALE_COMPLETED");
        assertEquals("SUCCESS", logs.get(0).getStatus());
        assertEquals(savedSale.getId(), logs.get(0).getEntityId());
    }

    @Test
    void completeSaleAppliesPercentageDiscountAndPaymentDetails() {
        Shop shop = createShop("F");
        User owner = createOwner(shop, "F");
        Product product = createProduct(shop, "Biscuits", "50.00", 10, 10);

        Sale savedSale = saleService.completeSale(
                List.of(CartItem.builder().productId(product.getId()).quantity(2).build()),
                shop,
                owner,
                "Ravi",
                "9999999999",
                "UPI",
                100.0,
                0.0,
                null,
                new BigDecimal("10"));

        assertEquals("Ravi", savedSale.getCustomerName());
        assertEquals("9999999999", savedSale.getCustomerPhone());
        assertEquals("UPI", savedSale.getPaymentMode());
        assertEquals(100.0, savedSale.getAmountReceived());
        assertEquals(0.0, savedSale.getChangeReturned());
        assertEquals(0, new BigDecimal("11.00").compareTo(savedSale.getDiscountAmount()));
        assertEquals(0, new BigDecimal("10").compareTo(savedSale.getDiscountPercent()));
        assertEquals(0, new BigDecimal("99.00").compareTo(savedSale.getTotalAmount()));
    }

    @Test
    void completeSalePersistsPrescriptionDetailsForRxMedicines() {
        Shop shop = createShop("RX");
        User owner = createOwner(shop, "RX");
        Product product = createProduct(shop, "Azithral 500", "96.00", 10, 12);
        product.setPrescriptionRequired(true);
        productRepository.save(product);

        Sale savedSale = saleService.completeSale(
                List.of(CartItem.builder().productId(product.getId()).quantity(1).build()),
                shop,
                owner,
                "Ravi",
                "9999999999",
                "Dr. Shah",
                LocalDate.now(),
                "RX-1001",
                true,
                "CASH",
                108.0,
                0.0,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertTrue(savedSale.isPrescriptionRequired());
        assertTrue(savedSale.isPrescriptionVerified());
        assertEquals("Dr. Shah", savedSale.getDoctorName());
        assertEquals("RX-1001", savedSale.getPrescriptionReference());
        assertEquals(LocalDate.now(), savedSale.getPrescriptionDate());
    }

    @Test
    void completeSaleRejectsRxMedicinesWithoutVerifiedPrescription() {
        Shop shop = createShop("RX2");
        User owner = createOwner(shop, "RX2");
        Product product = createProduct(shop, "Antibiotic", "120.00", 5, 12);
        product.setPrescriptionRequired(true);
        productRepository.save(product);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> saleService.completeSale(
                        List.of(CartItem.builder().productId(product.getId()).quantity(1).build()),
                        shop,
                        owner,
                        "Ravi",
                        "9999999999",
                        null,
                        LocalDate.now(),
                        null,
                        false,
                        "CASH",
                        120.0,
                        0.0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));

        assertEquals("Prescription must be verified before billing Rx medicines", exception.getMessage());
    }

    @Test
    void completeSaleRejectsDiscountGreaterThanTotal() {
        Shop shop = createShop("G");
        User owner = createOwner(shop, "G");
        Product product = createProduct(shop, "Soap", "20.00", 3, 0);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> saleService.completeSale(
                        List.of(CartItem.builder().productId(product.getId()).quantity(1).build()),
                        shop,
                        owner,
                        null,
                        null,
                        "CASH",
                        0.0,
                        0.0,
                        new BigDecimal("25.00"),
                        BigDecimal.ZERO));

        assertEquals("Discount cannot exceed total amount", exception.getMessage());
    }

    @Test
    void completeSaleRollsBackStockWhenAnyLineHasInsufficientInventory() {
        Shop shop = createShop("H");
        User owner = createOwner(shop, "H");
        Product enoughStock = createProduct(shop, "Bread", "40.00", 5, 0);
        Product lowStock = createProduct(shop, "Butter", "60.00", 1, 0);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> saleService.completeSale(
                        List.of(
                                CartItem.builder().productId(enoughStock.getId()).quantity(2).build(),
                                CartItem.builder().productId(lowStock.getId()).quantity(2).build()),
                        shop,
                        owner));

        assertEquals("Not enough sellable stock for product: Butter", exception.getMessage());
        assertEquals(5, productRepository.findById(enoughStock.getId()).orElseThrow().getStockQuantity());
        assertEquals(1, productRepository.findById(lowStock.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, saleRepository.findByShop(shop, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void completeSaleAggregatesDuplicateCartLinesBeforeDeductingStock() {
        Shop shop = createShop("I");
        User owner = createOwner(shop, "I");
        Product product = createProduct(shop, "Eggs", "12.00", 10, 0);

        Sale savedSale = saleService.completeSale(
                List.of(
                        CartItem.builder().productId(product.getId()).quantity(2).build(),
                        CartItem.builder().productId(product.getId()).quantity(3).build()),
                shop,
                owner);

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        List<SaleItem> items = saleItemRepository.findBySale(savedSale);

        assertEquals(5, items.get(0).getQuantity());
        assertEquals(5, updatedProduct.getStockQuantity());
        assertEquals(1, items.size());
    }

    @Test
    void completeSaleRejectsItemsFromAnotherShop() {
        Shop shop = createShop("B");
        User owner = createOwner(shop, "B");
        Shop otherShop = createShop("C");
        Product otherShopProduct = createProduct(otherShop, "Rice", "50.00", 7, 5);

        List<CartItem> cartItems = List.of(
                CartItem.builder().productId(otherShopProduct.getId()).quantity(1).build());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> saleService.completeSale(cartItems, shop, owner));

        assertTrue(exception.getMessage().contains("Product not found"));
        assertEquals(0, saleRepository.findByShop(shop, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        assertEquals(7, productRepository.findById(otherShopProduct.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void completeSaleSupportsLegacyProductsWithNullVersion() {
        Shop shop = createShop("LEG");
        User owner = createOwner(shop, "LEG");
        Product product = createProduct(shop, "Legacy Product", "25.00", 8, 5);

        jdbcTemplate.update("UPDATE product SET version = NULL WHERE id = ?", product.getId());

        Sale savedSale = saleService.completeSale(
                List.of(CartItem.builder().productId(product.getId()).quantity(2).build()),
                shop,
                owner);

        assertNotNull(savedSale.getId());
        assertEquals(6, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void cancelSaleRestoresStockAndMarksSaleCancelled() {
        Shop shop = createShop("D");
        User owner = createOwner(shop, "D");
        Product product = createProduct(shop, "Milk", "30.00", 6, 12);

        Sale savedSale = saleService.completeSale(
                List.of(CartItem.builder().productId(product.getId()).quantity(2).build()),
                shop,
                owner);

        saleService.cancelSale(savedSale.getId());

        Sale cancelledSale = saleRepository.findById(savedSale.getId()).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertEquals(SaleStatus.CANCELLED, cancelledSale.getStatus());
        assertEquals(6, updatedProduct.getStockQuantity());
        List<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc("SALE_CANCELLED");
        assertEquals("SUCCESS", logs.get(0).getStatus());
        assertEquals(savedSale.getId(), logs.get(0).getEntityId());
    }

    @Test
    void completeSaleConsumesBatchesInExpiryOrder() {
        Shop shop = createShop("BAT");
        User owner = createOwner(shop, "BAT");
        Product product = createProduct(shop, "Cough Syrup", "90.00", 0, 12);

        PurchaseBatch firstBatch = createBatch(product, shop, owner, "BATCH-A", LocalDate.now().plusDays(15), 4, "55.00", "90.00");
        PurchaseBatch secondBatch = createBatch(product, shop, owner, "BATCH-B", LocalDate.now().plusDays(45), 6, "56.00", "90.00");

        Sale sale = saleService.completeSale(
                List.of(CartItem.builder().productId(product.getId()).quantity(5).build()),
                shop,
                owner);

        PurchaseBatch updatedFirst = purchaseBatchRepository.findById(firstBatch.getId()).orElseThrow();
        PurchaseBatch updatedSecond = purchaseBatchRepository.findById(secondBatch.getId()).orElseThrow();
        List<SaleItem> items = saleItemRepository.findBySale(sale);
        List<SaleItemBatchAllocation> allocations = saleItemBatchAllocationRepository.findBySaleItem(items.get(0));

        assertEquals(0, updatedFirst.getAvailableQuantity());
        assertEquals(5, updatedSecond.getAvailableQuantity());
        assertEquals(2, allocations.size());
        assertEquals(5, allocations.stream().mapToInt(SaleItemBatchAllocation::getQuantity).sum());
        assertEquals(5, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void cancelSaleRestoresAllocatedBatchStock() {
        Shop shop = createShop("REST");
        User owner = createOwner(shop, "REST");
        Product product = createProduct(shop, "Antibiotic", "120.00", 0, 12);

        PurchaseBatch batch = createBatch(product, shop, owner, "REST-01", LocalDate.now().plusDays(30), 5, "82.00", "120.00");
        Sale sale = saleService.completeSale(
                List.of(CartItem.builder().productId(product.getId()).quantity(3).build()),
                shop,
                owner);

        saleService.cancelSale(sale.getId());

        PurchaseBatch restoredBatch = purchaseBatchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(5, restoredBatch.getAvailableQuantity());
        assertEquals(5, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void completeSaleRejectsExpiredBatchOnlyStock() {
        Shop shop = createShop("EXP");
        User owner = createOwner(shop, "EXP");
        Product product = createProduct(shop, "Expired Only", "70.00", 0, 5);

        createBatch(product, shop, owner, "EXP-01", LocalDate.now().minusDays(2), 5, "40.00", "70.00");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> saleService.completeSale(
                        List.of(CartItem.builder().productId(product.getId()).quantity(1).build()),
                        shop,
                        owner));

        assertEquals("Not enough sellable stock for product: Expired Only", exception.getMessage());
        assertEquals(5, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void completeSaleRejectsNonPositiveQuantity() {
        Shop shop = createShop("E");
        User owner = createOwner(shop, "E");
        Product product = createProduct(shop, "Oil", "100.00", 4, 18);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> saleService.completeSale(
                        List.of(CartItem.builder().productId(product.getId()).quantity(0).build()),
                        shop,
                        owner));

        assertEquals("Quantity must be greater than zero", exception.getMessage());
        assertEquals(4, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        List<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc("SALE_COMPLETED");
        assertEquals("FAILED", logs.get(0).getStatus());
    }

    private Shop createShop(String suffix) {
        return shopRepository.save(Shop.builder()
                .name("Test Shop " + suffix)
                .city("Mumbai")
                .state("MH")
                .planType(PlanType.BASIC)
                .active(true)
                .build());
    }

    private User createOwner(Shop shop, String suffix) {
        return userRepository.save(User.builder()
                .name("Owner " + suffix)
                .username("owner" + suffix + "@example.com")
                .phone("90000000" + suffix)
                .password("encoded-password")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.BASIC)
                .build());
    }

    private Product createProduct(Shop shop, String name, String price, int stockQuantity, int gstPercent) {
        return productRepository.save(Product.builder()
                .name(name)
                .price(new BigDecimal(price))
                .stockQuantity(stockQuantity)
                .gstPercent(gstPercent)
                .shop(shop)
                .active(true)
                .build());
    }

    private PurchaseBatch createBatch(Product product,
                                      Shop shop,
                                      User owner,
                                      String batchNumber,
                                      LocalDate expiryDate,
                                      int quantity,
                                      String purchasePrice,
                                      String salePrice) {
        PurchaseEntry entry = purchaseEntryRepository.save(PurchaseEntry.builder()
                .purchaseDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .supplierName("Supplier " + batchNumber)
                .supplierInvoiceNumber("INV-" + batchNumber)
                .shop(shop)
                .createdBy(owner)
                .totalAmount(new BigDecimal(purchasePrice).multiply(BigDecimal.valueOf(quantity)))
                .build());

        Product managedProduct = productRepository.findById(product.getId()).orElseThrow();
        managedProduct.setPurchasePrice(new BigDecimal(purchasePrice));
        managedProduct.setPrice(new BigDecimal(salePrice));
        managedProduct.setStockQuantity(managedProduct.getStockQuantity() + quantity);
        productRepository.save(managedProduct);

        return purchaseBatchRepository.save(PurchaseBatch.builder()
                .purchaseEntry(entry)
                .shop(shop)
                .product(managedProduct)
                .batchNumber(batchNumber)
                .expiryDate(expiryDate)
                .receivedQuantity(quantity)
                .availableQuantity(quantity)
                .purchasePrice(new BigDecimal(purchasePrice))
                .salePrice(new BigDecimal(salePrice))
                .mrp(new BigDecimal(salePrice))
                .createdAt(LocalDateTime.now())
                .active(true)
                .build());
    }
}
