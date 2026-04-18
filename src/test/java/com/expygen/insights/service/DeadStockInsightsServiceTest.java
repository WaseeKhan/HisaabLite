package com.expygen.insights.service;

import com.expygen.entity.Product;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.DeadStockReportDto;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadStockInsightsServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @InjectMocks
    private DeadStockInsightsService deadStockInsightsService;

    @Test
    void buildReportReturnsOnlyProductsIdleBeyondSelectedWindowOrNeverSold() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("Dead Stock Shop");

        Product deadStock = Product.builder()
                .id(10L)
                .name("Slow Syrup")
                .manufacturer("Acme Pharma")
                .barcode("8901000000010")
                .purchasePrice(new BigDecimal("50.00"))
                .price(new BigDecimal("72.00"))
                .stockQuantity(12)
                .active(true)
                .shop(shop)
                .build();

        Product activeMover = Product.builder()
                .id(11L)
                .name("Fast Tablet")
                .manufacturer("Acme Pharma")
                .barcode("8901000000011")
                .purchasePrice(new BigDecimal("12.00"))
                .price(new BigDecimal("18.00"))
                .stockQuantity(15)
                .active(true)
                .shop(shop)
                .build();

        Product neverSold = Product.builder()
                .id(12L)
                .name("New Capsule")
                .manufacturer("Nova Labs")
                .barcode("8901000000012")
                .purchasePrice(new BigDecimal("20.00"))
                .price(new BigDecimal("30.00"))
                .stockQuantity(7)
                .active(true)
                .shop(shop)
                .build();

        when(productRepository.findByShopAndActiveTrue(shop)).thenReturn(List.of(deadStock, activeMover, neverSold));
        when(saleItemRepository.findLastSoldAtByProduct(shop)).thenReturn(List.of(
                new Object[]{10L, LocalDateTime.now().minusDays(120)},
                new Object[]{11L, LocalDateTime.now().minusDays(12)}
        ));

        DeadStockReportDto report = deadStockInsightsService.buildReport(shop, 90, null, null);

        assertEquals(2, report.getRows().size());
        assertEquals("New Capsule", report.getRows().get(0).getProductName());
        assertEquals("Slow Syrup", report.getRows().get(1).getProductName());
        assertEquals("Never Sold", report.getRows().get(0).getStatus());
        assertEquals("₹740.00", report.getKpis().get(1).getValue());
    }
}
