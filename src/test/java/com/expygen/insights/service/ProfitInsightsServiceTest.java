package com.expygen.insights.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleItemBatchAllocation;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.ProfitMarginReportDto;
import com.expygen.repository.SaleItemBatchAllocationRepository;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;

@ExtendWith(MockitoExtension.class)
class ProfitInsightsServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;

    @InjectMocks
    private ProfitInsightsService profitInsightsService;

    @Test
    void buildReportUsesBatchAllocationCostForGrossProfit() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("Margin Shop");

        Product product = Product.builder()
                .id(10L)
                .name("Dolo 650")
                .manufacturer("Micro Labs")
                .purchasePrice(new BigDecimal("24.00"))
                .build();

        Sale sale = Sale.builder()
                .id(1L)
                .shop(shop)
                .status(SaleStatus.COMPLETED)
                .saleDate(LocalDateTime.of(2026, 4, 13, 12, 0))
                .build();

        SaleItem saleItem = SaleItem.builder()
                .id(20L)
                .sale(sale)
                .product(product)
                .quantity(2)
                .subtotal(new BigDecimal("64.00"))
                .gstAmount(new BigDecimal("0.00"))
                .totalWithGst(new BigDecimal("64.00"))
                .build();

        PurchaseBatch purchaseBatch = PurchaseBatch.builder()
                .id(30L)
                .purchasePrice(new BigDecimal("20.00"))
                .build();

        SaleItemBatchAllocation allocation = SaleItemBatchAllocation.builder()
                .id(40L)
                .saleItem(saleItem)
                .purchaseBatch(purchaseBatch)
                .quantity(2)
                .build();

        when(saleRepository.findByShopAndSaleDateBetweenOrderBySaleDateDesc(
                org.mockito.ArgumentMatchers.eq(shop),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(sale));
        when(saleItemRepository.findBySaleIn(List.of(sale))).thenReturn(List.of(saleItem));
        when(saleItemBatchAllocationRepository.findBySaleItemIn(List.of(saleItem))).thenReturn(List.of(allocation));

        ProfitMarginReportDto report = profitInsightsService.buildReport(shop, null, null, null, null);

        assertEquals(1, report.getRows().size());
        assertEquals(new BigDecimal("64.00"), report.getRows().get(0).getRevenue());
        assertEquals(new BigDecimal("40.00"), report.getRows().get(0).getEstimatedCost());
        assertEquals(new BigDecimal("24.00"), report.getRows().get(0).getGrossProfit());
        assertEquals(37.50d, report.getRows().get(0).getMarginPercent());
    }
}
