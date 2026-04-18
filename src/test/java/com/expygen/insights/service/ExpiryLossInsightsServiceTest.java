package com.expygen.insights.service;

import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Shop;
import com.expygen.repository.PurchaseBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiryLossInsightsServiceTest {

    @Mock
    private PurchaseBatchRepository purchaseBatchRepository;

    @InjectMocks
    private ExpiryLossInsightsService expiryLossInsightsService;

    @Test
    void buildReportCalculatesExpiredLossExposure() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("Expiry Shop");

        Product product = Product.builder()
                .id(10L)
                .name("Cough Syrup")
                .manufacturer("Acme Pharma")
                .barcode("8901000000100")
                .build();

        PurchaseBatch expiredBatch = PurchaseBatch.builder()
                .id(101L)
                .product(product)
                .shop(shop)
                .batchNumber("CS-EXP-1")
                .availableQuantity(5)
                .purchasePrice(new BigDecimal("40.00"))
                .mrp(new BigDecimal("60.00"))
                .salePrice(new BigDecimal("55.00"))
                .expiryDate(LocalDate.now().minusDays(4))
                .active(true)
                .build();

        when(purchaseBatchRepository.findByShopAndActiveTrueOrderByCreatedAtDesc(eq(shop), eq(PageRequest.of(0, 1000))))
                .thenReturn(new PageImpl<>(List.of(expiredBatch)));

        var report = expiryLossInsightsService.buildReport(shop, "EXPIRED", null, null);

        assertEquals(1, report.getRows().size());
        assertEquals("Expired", report.getRows().get(0).getStatus());
        assertEquals(200.0, report.getRows().get(0).getEstimatedCostLoss());
        assertEquals(300.0, report.getRows().get(0).getRetailValueAtRisk());
        assertEquals("₹200.00", report.getKpis().get(3).getValue());
        assertEquals("₹300.00", report.getKpis().get(4).getValue());
    }
}
