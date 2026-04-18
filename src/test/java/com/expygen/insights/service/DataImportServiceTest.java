package com.expygen.insights.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.expygen.dto.PurchaseEntryForm;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.SupplierRepository;
import com.expygen.service.PlanLimitService;
import com.expygen.service.PurchaseService;
import com.expygen.service.SupplierService;

@ExtendWith(MockitoExtension.class)
class DataImportServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private PurchaseBatchRepository purchaseBatchRepository;

    @Mock
    private PurchaseService purchaseService;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierService supplierService;

    @InjectMocks
    private DataImportService dataImportService;

    @Test
    void importProductsCreatesMedicineRowsFromValidCsv() {
        Shop shop = testShop();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                ("Medicine Name,Generic Name,Manufacturer,Barcode,Pack Size,Sale Price,MRP,Purchase Price,Stock Quantity,Min Stock,GST %,Prescription Required,Notes\n"
                        + "\"Dolo 650\",\"Paracetamol\",\"Micro Labs\",\"8901234567001\",\"15 tablets\",32,34,24,0,10,12,No,\"Fast moving\"\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(planLimitService.getProductLimit(shop)).thenReturn(500);
        when(productRepository.countByShopAndActiveTrue(shop)).thenReturn(0L);
        when(productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, "8901234567001")).thenReturn(false);
        when(productRepository.findByShopAndNameIgnoreCaseAndActiveTrue(shop, "Dolo 650")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = dataImportService.importProducts(shop, file);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();

        assertEquals(1, result.getImportedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("Dolo 650", saved.getName());
        assertEquals("8901234567001", saved.getBarcode());
        assertEquals(new BigDecimal("32"), saved.getPrice());
        assertEquals(new BigDecimal("34"), saved.getMrp());
        assertEquals(10, saved.getMinStock());
        assertEquals(12, saved.getGstPercent());
        assertEquals(false, saved.isPrescriptionRequired());
    }

    @Test
    void importProductsSkipsExistingBarcodeRows() {
        Shop shop = testShop();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                ("Medicine Name,Sale Price,Barcode\n"
                        + "\"Azithral 500\",96,8901234567002\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(planLimitService.getProductLimit(shop)).thenReturn(500);
        when(productRepository.countByShopAndActiveTrue(shop)).thenReturn(10L);
        when(productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, "8901234567002")).thenReturn(true);

        var result = dataImportService.importProducts(shop, file);

        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void importProductsRequiresTemplateHeaders() {
        Shop shop = testShop();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.csv",
                "text/csv",
                "Barcode,MRP\n8901,34\n".getBytes(StandardCharsets.UTF_8));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> dataImportService.importProducts(shop, file));

        assertEquals("Medicine Name column is missing from the CSV template.", exception.getMessage());
    }

    @Test
    void importOpeningStockQueuesValidBatchRowsIntoPurchaseFlow() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .id(15L)
                .name("Dolo 650")
                .barcode("8901234567001")
                .price(new BigDecimal("32"))
                .stockQuantity(0)
                .shop(shop)
                .active(true)
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening-stock.csv",
                "text/csv",
                ("Medicine Name,Barcode,Batch Number,Expiry Date,Quantity,Purchase Price,Sale Price,MRP,Supplier Name,Purchase Date,Supplier Invoice Number,Notes\n"
                        + "\"Dolo 650\",\"8901234567001\",\"DL650A1\",2027-10-31,40,24,32,34,\"Opening Stock Import\",2026-04-13,\"OPEN-001\",\"Go live\"\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(productRepository.searchProducts(shop, "8901234567001")).thenReturn(java.util.List.of(product));
        when(purchaseBatchRepository.findByShopAndProductAndBatchNumberIgnoreCaseAndActiveTrue(shop, product, "DL650A1"))
                .thenReturn(Optional.empty());

        var result = dataImportService.importOpeningStock(shop, owner, file);

        ArgumentCaptor<PurchaseEntryForm> formCaptor = ArgumentCaptor.forClass(PurchaseEntryForm.class);
        verify(purchaseService).recordPurchase(formCaptor.capture(), org.mockito.ArgumentMatchers.eq(shop), org.mockito.ArgumentMatchers.eq(owner));

        PurchaseEntryForm savedForm = formCaptor.getValue();
        assertEquals(1, result.getImportedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("Opening Stock Import", savedForm.getSupplierName());
        assertEquals("OPEN-001", savedForm.getSupplierInvoiceNumber());
        assertEquals(1, savedForm.getItems().size());
        assertEquals(15L, savedForm.getItems().get(0).getProductId());
        assertEquals("DL650A1", savedForm.getItems().get(0).getBatchNumber());
        assertEquals(40, savedForm.getItems().get(0).getQuantity());
    }

    @Test
    void importOpeningStockSkipsExistingBatchRows() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .id(18L)
                .name("Pantocid DSR")
                .barcode("8901234567003")
                .price(new BigDecimal("118"))
                .stockQuantity(0)
                .shop(shop)
                .active(true)
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening-stock.csv",
                "text/csv",
                ("Medicine Name,Batch Number,Quantity,Purchase Price\n"
                        + "\"Pantocid DSR\",\"PD2026C1\",25,89\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(productRepository.findByShopAndNameIgnoreCaseAndActiveTrue(shop, "Pantocid DSR")).thenReturn(Optional.of(product));
        when(purchaseBatchRepository.findByShopAndProductAndBatchNumberIgnoreCaseAndActiveTrue(shop, product, "PD2026C1"))
                .thenReturn(Optional.of(new com.expygen.entity.PurchaseBatch()));

        var result = dataImportService.importOpeningStock(shop, owner, file);

        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        verify(purchaseService, never()).recordPurchase(any(), any(), any());
    }

    @Test
    void importSuppliersCreatesSupplierRowsFromValidCsv() {
        Shop shop = testShop();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.csv",
                "text/csv",
                ("Supplier Name,Contact Person,Phone,GST Number,Address,Notes\n"
                        + "\"MediNova Distributors\",\"Rahul Sharma\",\"9876543210\",\"27AABCM1234D1Z9\",\"Andheri East, Mumbai\",\"Preferred supplier\"\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(supplierRepository.findByShopAndNameIgnoreCase(shop, "MediNova Distributors")).thenReturn(Optional.empty());
        when(supplierService.saveSupplier(org.mockito.ArgumentMatchers.eq(shop), any())).thenReturn(new Supplier());

        var result = dataImportService.importSuppliers(shop, file);

        assertEquals(1, result.getImportedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        verify(supplierService).saveSupplier(org.mockito.ArgumentMatchers.eq(shop), any());
    }

    @Test
    void importSuppliersSkipsExistingSupplierNames() {
        Shop shop = testShop();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.csv",
                "text/csv",
                ("Supplier Name,Phone\n"
                        + "\"WellCare Wholesale\",\"9988776655\"\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(supplierRepository.findByShopAndNameIgnoreCase(shop, "WellCare Wholesale"))
                .thenReturn(Optional.of(new Supplier()));

        var result = dataImportService.importSuppliers(shop, file);

        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        verify(supplierService, never()).saveSupplier(any(), any());
    }

    private Shop testShop() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("Expygen Test Shop");
        shop.setPlanType(PlanType.PREMIUM);
        return shop;
    }

    private User testOwner(Shop shop) {
        User user = new User();
        user.setId(5L);
        user.setName("Owner");
        user.setUsername("owner@example.com");
        user.setRole(Role.OWNER);
        user.setShop(shop);
        user.setCurrentPlan(PlanType.PREMIUM);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
