package com.expygen.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.expygen.admin.service.AuditService;
import com.expygen.dto.ProductBatchVisibility;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.service.BatchInventoryVisibilityService;
import com.expygen.service.BarcodeLabelService;
import com.expygen.repository.UserRepository;
import com.expygen.service.PlanLimitService;
import com.expygen.service.ProductBarcodeService;
import com.expygen.service.ProductService;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductService productService;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private AuditService auditService;

    @Mock
    private PurchaseBatchRepository purchaseBatchRepository;

    @Mock
    private BatchInventoryVisibilityService batchInventoryVisibilityService;

    @Mock
    private BarcodeLabelService barcodeLabelService;

    @Mock
    private ProductBarcodeService productBarcodeService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProductController productController;

    @Test
    void saveOrUpdateProductCreatesNewProductForCurrentShop() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product formProduct = Product.builder()
                .name("Tea")
                .description("Leaf tea")
                .barcode("890100000001")
                .genericName("Caffeine")
                .manufacturer("Health Labs")
                .packSize("10 tabs")
                .price(new BigDecimal("99.00"))
                .mrp(new BigDecimal("110.00"))
                .purchasePrice(new BigDecimal("74.00"))
                .stockQuantity(10)
                .gstPercent(5)
                .prescriptionRequired(true)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(planLimitService.getProductLimit(shop)).thenReturn(1000);
        when(planLimitService.canAddProduct(shop)).thenReturn(true);
        when(productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, "890100000001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });

        String view = productController.saveOrUpdateProduct(
                formProduct,
                new ExtendedModelMap(),
                authentication,
                new RedirectAttributesModelMap());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();

        assertEquals("redirect:/products", view);
        assertEquals(shop, savedProduct.getShop());
        assertEquals(true, savedProduct.isActive());
        assertEquals(5, savedProduct.getMinStock());
        assertEquals("890100000001", savedProduct.getBarcode());
        assertEquals("Caffeine", savedProduct.getGenericName());
        assertEquals("Health Labs", savedProduct.getManufacturer());
        assertEquals("10 tabs", savedProduct.getPackSize());
        assertEquals(new BigDecimal("110.00"), savedProduct.getMrp());
        assertEquals(new BigDecimal("74.00"), savedProduct.getPurchasePrice());
        assertEquals(true, savedProduct.isPrescriptionRequired());
        verify(auditService).logAction(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveOrUpdateProductUpdatesManagedEntityInsteadOfDetachedFormObject() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product existingProduct = Product.builder()
                .name("Old Name")
                .description("Old")
                .barcode("OLDCODE")
                .genericName("Old Salt")
                .manufacturer("Old Pharma")
                .packSize("1 strip")
                .price(new BigDecimal("45.00"))
                .mrp(new BigDecimal("50.00"))
                .purchasePrice(new BigDecimal("30.00"))
                .stockQuantity(7)
                .minStock(2)
                .gstPercent(18)
                .prescriptionRequired(false)
                .shop(shop)
                .active(true)
                .build();
        existingProduct.setId(10L);
        existingProduct.setVersion(1L);

        Product submittedProduct = Product.builder()
                .id(10L)
                .name("New Name")
                .description("Updated")
                .barcode("NEWCODE")
                .genericName("Paracetamol")
                .manufacturer("MediCorp")
                .packSize("15 tabs")
                .price(new BigDecimal("60.00"))
                .mrp(new BigDecimal("65.00"))
                .purchasePrice(new BigDecimal("42.00"))
                .stockQuantity(15)
                .minStock(3)
                .gstPercent(12)
                .prescriptionRequired(true)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrueAndIdNot(shop, "NEWCODE", 10L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String view = productController.saveOrUpdateProduct(
                submittedProduct,
                new ExtendedModelMap(),
                authentication,
                new RedirectAttributesModelMap());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertEquals("redirect:/products", view);
        assertSame(existingProduct, savedProduct);
        assertEquals("New Name", existingProduct.getName());
        assertEquals(15, existingProduct.getStockQuantity());
        assertEquals(new BigDecimal("60.00"), existingProduct.getPrice());
        assertEquals("NEWCODE", existingProduct.getBarcode());
        assertEquals("Paracetamol", existingProduct.getGenericName());
        assertEquals("MediCorp", existingProduct.getManufacturer());
        assertEquals("15 tabs", existingProduct.getPackSize());
        assertEquals(new BigDecimal("65.00"), existingProduct.getMrp());
        assertEquals(new BigDecimal("42.00"), existingProduct.getPurchasePrice());
        assertEquals(true, existingProduct.isPrescriptionRequired());
        verify(planLimitService, never()).canAddProduct(shop);
    }

    @Test
    void saveOrUpdateProductRejectsDuplicateBarcodeForSameShop() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product formProduct = Product.builder()
                .name("Crocin")
                .barcode("890100000111")
                .price(new BigDecimal("45.00"))
                .stockQuantity(5)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(planLimitService.getProductLimit(shop)).thenReturn(1000);
        when(planLimitService.canAddProduct(shop)).thenReturn(true);
        when(productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, "890100000111")).thenReturn(true);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = productController.saveOrUpdateProduct(formProduct, model, authentication, new RedirectAttributesModelMap());

        assertEquals("product-form", view);
        assertEquals("Barcode already exists for another medicine in your shop. Use a unique code before saving.",
                model.getAttribute("error"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void checkBarcodeAvailabilityNormalizesAndRejectsDuplicateForCurrentShop() {
        Shop shop = testShop();
        User owner = testOwner(shop);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, "890100000111"))
                .thenReturn(true);

        ResponseEntity<Map<String, Object>> response = productController.checkBarcodeAvailability(" 8901 0000 0111 ", null, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("890100000111", response.getBody().get("normalizedBarcode"));
        assertEquals(false, response.getBody().get("available"));
        assertEquals("duplicate", response.getBody().get("state"));
    }

    @Test
    void downloadBarcodeLabelReturnsPdfForAuthorizedProduct() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .name("Dolo 650")
                .barcode("8901234567001")
                .price(new BigDecimal("32.00"))
                .shop(shop)
                .stockQuantity(4)
                .build();
        product.setId(21L);
        product.setVersion(1L);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findById(21L)).thenReturn(Optional.of(product));
        when(barcodeLabelService.generateProductLabel(product)).thenReturn(new byte[] { 1, 2, 3 });

        ResponseEntity<byte[]> response = productController.downloadBarcodeLabel(21L, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
        assertEquals(3, response.getBody().length);
    }

    @Test
    void downloadBarcodeSheetReturnsPdfForSelectedBarcodeReadyProducts() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product printable = Product.builder()
                .id(41L)
                .name("Dolo 650")
                .barcode("8901234567001")
                .price(new BigDecimal("32.00"))
                .shop(shop)
                .active(true)
                .build();
        Product skipped = Product.builder()
                .id(42L)
                .name("No Barcode")
                .price(new BigDecimal("54.00"))
                .shop(shop)
                .active(true)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findByShopAndActiveTrueAndIdIn(shop, List.of(41L, 42L)))
                .thenReturn(List.of(printable, skipped));
        when(barcodeLabelService.generateProductLabelSheet(List.of(printable))).thenReturn(new byte[] { 9, 8, 7 });

        ResponseEntity<byte[]> response = productController.downloadBarcodeSheet(List.of(41L, 42L), authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
        assertEquals(3, response.getBody().length);
    }

    @Test
    void downloadRepeatedBarcodeSheetReturnsPdfForSingleProductQuantity() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .name("Dolo 650")
                .barcode("8901234567001")
                .price(new BigDecimal("32.00"))
                .shop(shop)
                .active(true)
                .build();
        product.setId(61L);
        product.setVersion(1L);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findById(61L)).thenReturn(Optional.of(product));
        when(barcodeLabelService.generateRepeatedProductLabelSheet(product, 24, BarcodeLabelService.LabelSheetSize.SHEET_40))
                .thenReturn(new byte[] { 4, 5, 6 });

        ResponseEntity<byte[]> response = productController.downloadRepeatedBarcodeSheet(61L, 24, "40", authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
        assertEquals(3, response.getBody().length);
    }

    @Test
    void downloadRepeatedBarcodeSheetRejectsOutOfRangeQuantity() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .name("Dolo 650")
                .barcode("8901234567001")
                .price(new BigDecimal("32.00"))
                .shop(shop)
                .active(true)
                .build();
        product.setId(62L);
        product.setVersion(1L);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findById(62L)).thenReturn(Optional.of(product));

        ResponseEntity<byte[]> response = productController.downloadRepeatedBarcodeSheet(62L, 0, "medium", authentication);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("text/plain; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    void downloadBarcodeSheetRejectsSelectionWithoutBarcodeReadyProducts() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product skipped = Product.builder()
                .id(52L)
                .name("No Barcode")
                .price(new BigDecimal("54.00"))
                .shop(shop)
                .active(true)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findByShopAndActiveTrueAndIdIn(shop, List.of(52L)))
                .thenReturn(List.of(skipped));

        ResponseEntity<byte[]> response = productController.downloadBarcodeSheet(List.of(52L), authentication);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("text/plain; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    void generateInternalBarcodeSavesBarcodeAndReturnsLabelUrl() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .name("Shelcal 500")
                .price(new BigDecimal("145.00"))
                .shop(shop)
                .stockQuantity(8)
                .build();
        product.setId(31L);
        product.setVersion(1L);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findById(31L)).thenReturn(Optional.of(product));
        when(productBarcodeService.generateInternalBarcode(product)).thenReturn("2900000000318");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<Map<String, Object>> response = productController.generateInternalBarcode(31L, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("2900000000318", response.getBody().get("barcode"));
        assertEquals("/products/31/barcode-label", response.getBody().get("labelUrl"));
        assertEquals("2900000000318", product.getBarcode());
    }

    @Test
    void searchProductsLiveIncludesBatchAndExpiryVisibility() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .id(12L)
                .name("Azithro 500")
                .price(new BigDecimal("120.00"))
                .stockQuantity(16)
                .minStock(4)
                .gstPercent(12)
                .shop(shop)
                .active(true)
                .build();
        ProductBatchVisibility visibility = ProductBatchVisibility.builder()
                .productId(12L)
                .batchManaged(true)
                .activeBatchCount(2)
                .liveBatchStock(12)
                .sellableStock(10)
                .nearExpiryBatchCount(1)
                .expiredBatchCount(0)
                .nextSellableExpiryDate(LocalDate.of(2026, 5, 10))
                .lowStock(false)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findByShopAndActiveTrue(shop, org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(product)));
        when(batchInventoryVisibilityService.summarizeProducts(shop, List.of(product)))
                .thenReturn(Map.of(12L, visibility));

        ResponseEntity<Map<String, Object>> response = productController.searchProductsLive(null, 0, authentication);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstProduct = (Map<String, Object>) ((List<?>) response.getBody().get("products")).get(0);
        assertEquals(10, firstProduct.get("sellableStock"));
        assertEquals(1, firstProduct.get("nearExpiryBatchCount"));
        assertEquals(LocalDate.of(2026, 5, 10), firstProduct.get("nextSellableExpiryDate"));
        assertFalse((Boolean) firstProduct.get("lowStock"));
    }

    private Shop testShop() {
        return Shop.builder()
                .id(1L)
                .name("Core Shop")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build();
    }

    private User testOwner(Shop shop) {
        return User.builder()
                .id(1L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .build();
    }
}
