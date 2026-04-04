package com.hisaablite.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.hisaablite.dto.CartItem;
import com.hisaablite.dto.SaleHistoryDTO;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleStatus;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.PdfService;
import com.hisaablite.service.ProductService;
import com.hisaablite.service.SaleService;
import com.hisaablite.service.WhatsAppService;

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
        when(saleService.completeSale(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(savedSale);

        String view = saleController.completeSale(
                "Walk In",
                "98765 43210",
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
        when(saleRepository.countByShop(shop)).thenReturn(1L);
        when(saleRepository.getTotalRevenueByShop(shop)).thenReturn(new BigDecimal("120.00"));
        when(saleRepository.countByShopAndStatus(shop, SaleStatus.COMPLETED)).thenReturn(1L);
        when(saleRepository.countByShopAndStatus(shop, SaleStatus.CANCELLED)).thenReturn(0L);

        String view = saleController.salesHistory(model, authentication, 0, null, null, null, null);

        assertEquals("sales-history", view);
        SaleHistoryDTO firstSale = (SaleHistoryDTO) ((List<?>) model.getAttribute("sales")).get(0);
        assertEquals("9999999999", firstSale.getCustomerPhone());
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
        when(whatsAppService.sendInvoiceWithPdf(eq(sale), eq("9999999999"))).thenReturn(true);

        ResponseEntity<?> response = saleController.sendWhatsAppWithPdf(31L, "9999999999", null, authentication);

        assertEquals(200, response.getStatusCode().value());
        verify(whatsAppService).sendInvoiceWithPdf(eq(sale), eq("9999999999"));
    }

    private Shop testShop() {
        return Shop.builder()
                .id(1L)
                .name("Core Shop")
                .panNumber("ABCDE1234F")
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
