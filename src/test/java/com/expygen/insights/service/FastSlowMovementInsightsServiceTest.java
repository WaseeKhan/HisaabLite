package com.expygen.insights.service;

import com.expygen.entity.Product;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.Shop;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FastSlowMovementInsightsServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private FastSlowMovementInsightsService fastSlowMovementInsightsService;

    @Test
    void buildReportClassifiesFastSlowAndNoMovementProducts() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("Movement Shop");

        Product fastProduct = Product.builder()
                .id(10L)
                .name("Dolo 650")
                .manufacturer("Acme Pharma")
                .barcode("8901000000010")
                .stockQuantity(30)
                .active(true)
                .build();

        Product slowProduct = Product.builder()
                .id(11L)
                .name("Rare Syrup")
                .manufacturer("Acme Pharma")
                .barcode("8901000000011")
                .stockQuantity(12)
                .active(true)
                .build();

        Product noMovementProduct = Product.builder()
                .id(12L)
                .name("Shelf Capsule")
                .manufacturer("Nova Labs")
                .barcode("8901000000012")
                .stockQuantity(8)
                .active(true)
                .build();

        Sale saleOne = Sale.builder().id(100L).shop(shop).saleDate(LocalDateTime.now().minusDays(2)).build();
        Sale saleTwo = Sale.builder().id(101L).shop(shop).saleDate(LocalDateTime.now().minusDays(5)).build();

        SaleItem fastSale = SaleItem.builder()
                .id(1000L)
                .sale(saleOne)
                .product(fastProduct)
                .quantity(40)
                .totalWithGst(new BigDecimal("1280.00"))
                .build();

        SaleItem slowSale = SaleItem.builder()
                .id(1001L)
                .sale(saleTwo)
                .product(slowProduct)
                .quantity(3)
                .totalWithGst(new BigDecimal("270.00"))
                .build();

        when(productRepository.findByShopAndActiveTrue(shop)).thenReturn(List.of(fastProduct, slowProduct, noMovementProduct));
        when(saleRepository.findByShopAndSaleDateBetweenOrderBySaleDateDesc(
                shop,
                LocalDate.of(2026, 4, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 30).plusDays(1).atStartOfDay()
        )).thenReturn(List.of(saleOne, saleTwo));
        when(saleItemRepository.findBySaleIn(List.of(saleOne, saleTwo))).thenReturn(List.of(fastSale, slowSale));
        when(saleItemRepository.findLastSoldAtByProduct(shop)).thenReturn(List.of(
                new Object[]{10L, LocalDateTime.now().minusDays(2)},
                new Object[]{11L, LocalDateTime.now().minusDays(5)}
        ));

        var report = fastSlowMovementInsightsService.buildReport(
                shop,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "ALL",
                null,
                null
        );

        assertEquals(3, report.getRows().size());
        assertEquals("Fast", report.getRows().get(0).getMovementStatus());
        assertEquals("Slow", report.getRows().get(1).getMovementStatus());
        assertEquals("No Movement", report.getRows().get(2).getMovementStatus());
        assertEquals("1", report.getKpis().get(0).getValue());
        assertEquals("1", report.getKpis().get(1).getValue());
        assertEquals("1", report.getKpis().get(2).getValue());
        assertEquals("43", report.getKpis().get(3).getValue());
        assertEquals("₹1,550.00", report.getKpis().get(4).getValue());
    }
}
