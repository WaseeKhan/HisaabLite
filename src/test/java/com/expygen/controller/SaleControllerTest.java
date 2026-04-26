package com.expygen.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.expygen.dto.CartItem;
import com.expygen.dto.CustomerHistoryDTO;
import com.expygen.dto.ProductLookupResult;
import com.expygen.dto.SaleBatchTraceSummaryDTO;
import com.expygen.dto.SaleHistoryDTO;
import com.expygen.dto.SoldBatchTraceDTO;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.Role;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.PdfService;
import com.expygen.service.ProductService;
import com.expygen.service.PrescriptionDocumentService;
import com.expygen.service.SaleBatchTraceService;
import com.expygen.service.SaleService;
import com.expygen.service.SubscriptionAccessService;
import com.expygen.service.WhatsAppService;

import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
class SaleControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private SaleService saleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private PdfService pdfService;

    @Mock
    private SaleBatchTraceService saleBatchTraceService;

    @Mock
    private PrescriptionDocumentService prescriptionDocumentService;

    @Mock
    private SubscriptionAccessService subscriptionAccessService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpSession session;

    @InjectMocks
    private SaleController saleController;

    @Test
    void completeSaleRedirectIncludesInvoiceIdAndEncodedPhone() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Sale savedSale = Sale.builder()
                .shop(shop)
                .createdBy(owner)
                .totalAmount(new BigDecimal("235.00"))
                .customerPhone("98765 43210")
                .status(SaleStatus.COMPLETED)
                .build();
        savedSale.setId(88L);

        List<CartItem> cart = List.of(CartItem.builder()
                .productId(1L)
                .productName("Rice")
                .price(new BigDecimal("100.00"))
                .quantity(2)
                .subtotal(new BigDecimal("200.00"))
                .gstPercent(5)
                .gstAmount(new BigDecimal("10.00"))
                .totalWithGst(new BigDecimal("210.00"))
                .build());

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(session.getAttribute("cart")).thenReturn(cart);
        when(saleService.completeSale(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(savedSale);

        String view = saleController.completeSale(
                "Walk In",
                "98765 43210",
                null,
                null,
                null,
                false,
                null,
                "CASH",
                235.0,
                25.0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                session,
                authentication,
                new RedirectAttributesModelMap());

        assertEquals("redirect:/sales/new?saved=true&invoiceId=88&phone=98765%2043210", view);
        verify(session).removeAttribute("cart");
    }

    @Test
    void salesHistoryAddsCustomerPhoneToModelDtos() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Sale sale = Sale.builder()
                .shop(shop)
                .createdBy(owner)
                .saleDate(LocalDateTime.now().minusHours(2))
                .totalAmount(new BigDecimal("120.00"))
                .customerName("Ravi")
                .customerPhone("9999999999")
                .status(SaleStatus.COMPLETED)
                .build();
        sale.setId(10L);

        Model model = new ExtendedModelMap();
        Page<Sale> salesPage = new PageImpl<>(List.of(sale));

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(saleRepository.findByShop(any(), any())).thenReturn(salesPage);
        when(subscriptionAccessService.getPlanName(shop)).thenReturn("PRO");
        when(subscriptionAccessService.canUseWhatsAppIntegration(shop)).thenReturn(true);
        when(subscriptionAccessService.canAccessInsights(shop)).thenReturn(true);
        when(saleRepository.countByShop(shop)).thenReturn(1L);
        when(saleRepository.getTotalRevenueByShop(shop)).thenReturn(new BigDecimal("120.00"));
        when(saleRepository.countByShopAndStatus(shop, SaleStatus.COMPLETED)).thenReturn(1L);
        when(saleRepository.countByShopAndStatus(shop, SaleStatus.CANCELLED)).thenReturn(0L);
        when(saleBatchTraceService.summarizeSales(List.of(sale))).thenReturn(Map.of(
                sale.getId(),
                SaleBatchTraceSummaryDTO.builder()
                        .batchManaged(true)
                        .tracedBatchCount(2)
                        .tracedUnits(5)
                        .nextExpiryDate(LocalDate.now().plusDays(21))
                        .expiredBatchCount(0)
                        .build()));

        String view = saleController.salesHistory(model, authentication, 0, null, null, null, null);

        assertEquals("sales-history", view);
        SaleHistoryDTO firstSale = (SaleHistoryDTO) ((List<?>) model.getAttribute("sales")).get(0);
        assertEquals("9999999999", firstSale.getCustomerPhone());
        assertEquals(null, firstSale.getDoctorName());
        assertTrue(firstSale.isBatchManaged());
        assertEquals(2, firstSale.getTracedBatchCount());
    }

    @Test
    void searchSalesHistoryReturnsCustomerPhoneInAjaxResponse() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Sale sale = Sale.builder()
                .shop(shop)
                .createdBy(owner)
                .saleDate(LocalDateTime.now().minusDays(1))
                .totalAmount(new BigDecimal("450.00"))
                .customerName("Customer")
                .customerPhone("8888888888")
                .status(SaleStatus.COMPLETED)
                .build();
        sale.setId(25L);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(saleRepository.findByShop(any(), any())).thenReturn(new PageImpl<>(List.of(sale)));
        when(saleBatchTraceService.summarizeSales(List.of(sale))).thenReturn(Map.of(
                sale.getId(),
                SaleBatchTraceSummaryDTO.builder()
                        .batchManaged(true)
                        .tracedBatchCount(1)
                        .tracedUnits(2)
                        .nextExpiryDate(LocalDate.now().plusDays(14))
                        .expiredBatchCount(0)
                        .build()));

        ResponseEntity<Map<String, Object>> response = saleController.searchSalesHistory(
                authentication,
                null,
                null,
                null,
                0,
                "saleDate",
                "desc");

        assertEquals(200, response.getStatusCode().value());
        Object firstSale = ((List<?>) response.getBody().get("sales")).get(0);
        assertInstanceOf(SaleHistoryDTO.class, firstSale);
        assertEquals("8888888888", ((SaleHistoryDTO) firstSale).getCustomerPhone());
        assertTrue(((SaleHistoryDTO) firstSale).isBatchManaged());
        assertEquals(1, ((SaleHistoryDTO) firstSale).getTracedBatchCount());
    }

    @Test
    void viewInvoiceAddsSoldBatchTraceToModel() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Sale sale = Sale.builder()
                .shop(shop)
                .createdBy(owner)
                .status(SaleStatus.COMPLETED)
                .saleDate(LocalDateTime.now())
                .build();
        sale.setId(31L);

        SaleItem item = SaleItem.builder()
                .sale(sale)
                .product(Product.builder().name("Paracetamol").build())
                .quantity(2)
                .priceAtSale(new BigDecimal("25.00"))
                .subtotal(new BigDecimal("50.00"))
                .gstPercent(12)
                .gstAmount(new BigDecimal("6.00"))
                .totalWithGst(new BigDecimal("56.00"))
                .build();
        item.setId(91L);

        Model model = new ExtendedModelMap();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(saleRepository.findByIdWithShop(sale.getId())).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySale(sale)).thenReturn(List.of(item));
        when(saleBatchTraceService.getBatchTraceBySaleItem(List.of(item))).thenReturn(Map.of(
                item.getId(),
                List.of(SoldBatchTraceDTO.builder()
                        .batchNumber("BATCH-01")
                        .quantity(2)
                        .expiryDate(LocalDate.now().plusDays(30))
                        .expired(false)
                        .build())));

        String view = saleController.viewInvoice(sale.getId(), model, authentication);

        assertEquals("invoice", view);
        assertEquals(List.of(item), model.getAttribute("items"));
        Map<?, ?> batchTrace = (Map<?, ?>) model.getAttribute("batchTraceBySaleItemId");
        assertTrue(batchTrace.containsKey(item.getId()));
    }

    @Test
    void customerHistoryReturnsRecentCustomerSnapshot() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .id(77L)
                .name("Pantocid DSR")
                .build();

        Sale sale = Sale.builder()
                .shop(shop)
                .createdBy(owner)
                .saleDate(LocalDateTime.now().minusDays(2))
                .totalAmount(new BigDecimal("245.00"))
                .customerName("Aarav")
                .customerPhone("9999999999")
                .doctorName("Dr. Shah")
                .prescriptionRequired(true)
                .status(SaleStatus.COMPLETED)
                .build();
        sale.setId(51L);

        SaleItem saleItem = SaleItem.builder()
                .sale(sale)
                .product(product)
                .quantity(1)
                .priceAtSale(new BigDecimal("245.00"))
                .subtotal(new BigDecimal("245.00"))
                .gstPercent(12)
                .gstAmount(new BigDecimal("29.40"))
                .totalWithGst(new BigDecimal("274.40"))
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(saleRepository.findTop5ByShopAndCustomerPhoneAndStatusOrderBySaleDateDesc(shop, "9999999999", SaleStatus.COMPLETED))
                .thenReturn(List.of(sale));
        when(saleRepository.countByShopAndCustomerPhoneAndStatus(shop, "9999999999", SaleStatus.COMPLETED))
                .thenReturn(4L);
        when(saleRepository.getLifetimeSpendByCustomerPhoneAndStatus(shop, "9999999999", SaleStatus.COMPLETED))
                .thenReturn(new BigDecimal("1220.00"));
        when(saleItemRepository.findBySaleIn(List.of(sale))).thenReturn(List.of(saleItem));

        CustomerHistoryDTO response = saleController.getCustomerHistory("9999999999", authentication);

        assertTrue(response.isFound());
        assertEquals("Aarav", response.getCustomerName());
        assertEquals("Dr. Shah", response.getLastDoctorName());
        assertEquals(4L, response.getVisitCount());
        assertEquals("Pantocid DSR", response.getRecentMedicines().get(0));
        assertEquals(51L, response.getRecentSales().get(0).getSaleId());
    }

    @Test
    void updateQuantityRejectsWhenRequestedQtyExceedsStock() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .name("Soap")
                .price(new BigDecimal("30.00"))
                .stockQuantity(3)
                .gstPercent(5)
                .shop(shop)
                .active(true)
                .build();
        product.setId(5L);

        CartItem cartItem = CartItem.builder()
                .productId(5L)
                .productName("Soap")
                .price(new BigDecimal("30.00"))
                .quantity(3)
                .subtotal(new BigDecimal("90.00"))
                .gstPercent(5)
                .gstAmount(new BigDecimal("4.50"))
                .totalWithGst(new BigDecimal("94.50"))
                .build();

        when(session.getAttribute("cart")).thenReturn(Collections.singletonList(cartItem));
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productService.getProductByIdAndShop(5L, shop)).thenReturn(Optional.of(product));
        when(saleService.getSellableStockForProduct(product)).thenReturn(3);

        ResponseEntity<?> response = saleController.updateQuantity(0, 1, session, authentication);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("Only 3 items available"));
    }

    @Test
    void searchProductsReturnsSellableStockForScannerLookup() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .id(9L)
                .name("Paracetamol 500")
                .barcode("8901234567890")
                .genericName("Paracetamol")
                .manufacturer("ABC Pharma")
                .packSize("10 Tablets")
                .price(new BigDecimal("42.00"))
                .stockQuantity(25)
                .gstPercent(12)
                .prescriptionRequired(false)
                .shop(shop)
                .active(true)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productService.searchProducts("8901234567890", shop)).thenReturn(List.of(product));
        when(saleService.getSellableStockForProduct(product)).thenReturn(7);

        List<ProductLookupResult> results = saleController.searchProducts("8901234567890", authentication);

        assertEquals(1, results.size());
        assertEquals("Paracetamol 500", results.get(0).getName());
        assertEquals(7, results.get(0).getSellableStock());
        assertEquals("8901234567890", results.get(0).getBarcode());
    }

    @Test
    void updateQuantityRejectsWhenRequestedQtyExceedsSellableBatchStock() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product product = Product.builder()
                .name("Cough Syrup")
                .price(new BigDecimal("85.00"))
                .stockQuantity(20)
                .gstPercent(12)
                .shop(shop)
                .active(true)
                .build();
        product.setId(15L);

        CartItem cartItem = CartItem.builder()
                .productId(15L)
                .productName("Cough Syrup")
                .price(new BigDecimal("85.00"))
                .quantity(3)
                .subtotal(new BigDecimal("255.00"))
                .gstPercent(12)
                .gstAmount(new BigDecimal("30.60"))
                .totalWithGst(new BigDecimal("285.60"))
                .build();

        when(session.getAttribute("cart")).thenReturn(Collections.singletonList(cartItem));
        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productService.getProductByIdAndShop(15L, shop)).thenReturn(Optional.of(product));
        when(saleService.getSellableStockForProduct(product)).thenReturn(3);

        ResponseEntity<?> response = saleController.updateQuantity(0, 1, session, authentication);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("Only 3 items available"));
    }

    @Test
    void sendWhatsAppPdfWithoutUploadedFileUsesGeneratedPdfAttachmentFlow() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Sale sale = Sale.builder()
                .shop(shop)
                .customerName("Ravi")
                .customerPhone("9999999999")
                .totalAmount(new BigDecimal("120.00"))
                .status(SaleStatus.COMPLETED)
                .build();
        sale.setId(31L);

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(saleRepository.findByIdWithShop(31L)).thenReturn(Optional.of(sale));
        when(subscriptionAccessService.canUseWhatsAppIntegration(shop)).thenReturn(true);
        when(whatsAppService.sendInvoiceWithPdf(eq(sale), eq("9999999999"))).thenReturn(true);

        ResponseEntity<?> response = saleController.sendWhatsAppWithPdf(31L, "9999999999", null, authentication);

        assertEquals(200, response.getStatusCode().value());
        verify(whatsAppService).sendInvoiceWithPdf(eq(sale), eq("9999999999"));
    }

    private Shop testShop() {
        return Shop.builder()
                .id(1L)
                .name("Core Shop")
                .planType(PlanType.PRO)
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
                .currentPlan(PlanType.PRO)
                .build();
    }
}
