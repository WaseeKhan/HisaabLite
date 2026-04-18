package com.expygen.insights.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleItemBatchAllocation;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.insights.dto.ProfitMarginReportDto;
import com.expygen.insights.dto.ProfitMarginRowDto;
import com.expygen.insights.dto.TopProductSummaryDto;
import com.expygen.repository.SaleItemBatchAllocationRepository;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfitInsightsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;

    public ProfitMarginReportDto buildReport(Shop shop, LocalDate fromDate, LocalDate toDate, String keyword, String manufacturer) {
        List<Sale> sales = findCompletedSales(shop, fromDate, toDate);
        List<SaleItem> saleItems = saleItemRepository.findBySaleIn(sales);
        List<SaleItemBatchAllocation> allocations = saleItems.isEmpty()
                ? List.of()
                : saleItemBatchAllocationRepository.findBySaleItemIn(saleItems);

        Map<Long, List<SaleItemBatchAllocation>> allocationsBySaleItemId = allocations.stream()
                .collect(Collectors.groupingBy(allocation -> allocation.getSaleItem().getId()));

        Map<Long, ProfitAccumulator> byProduct = new LinkedHashMap<>();

        for (SaleItem item : saleItems) {
            if (item.getProduct() == null) {
                continue;
            }

            if (!matchesFilter(item, keyword, manufacturer)) {
                continue;
            }

            ProfitAccumulator accumulator = byProduct.computeIfAbsent(
                    item.getProduct().getId(),
                    ignored -> new ProfitAccumulator(
                            item.getProduct().getId(),
                            item.getProduct().getName(),
                            item.getProduct().getManufacturer()));

            BigDecimal revenue = safe(item.getTotalWithGst()).compareTo(BigDecimal.ZERO) > 0
                    ? safe(item.getTotalWithGst())
                    : safe(item.getSubtotal()).add(safe(item.getGstAmount()));
            BigDecimal estimatedCost = estimateCost(item, allocationsBySaleItemId.get(item.getId()));
            accumulator.quantitySold += item.getQuantity() != null ? item.getQuantity() : 0;
            accumulator.revenue = accumulator.revenue.add(revenue);
            accumulator.estimatedCost = accumulator.estimatedCost.add(estimatedCost);

            LocalDateTime saleDate = item.getSale() != null ? item.getSale().getSaleDate() : null;
            if (saleDate != null && (accumulator.lastSoldAt == null || saleDate.isAfter(accumulator.lastSoldAt))) {
                accumulator.lastSoldAt = saleDate;
            }
        }

        List<ProfitMarginRowDto> rows = byProduct.values().stream()
                .map(this::toRow)
                .sorted(Comparator.comparing(ProfitMarginRowDto::getGrossProfit, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .toList();

        BigDecimal totalRevenue = rows.stream().map(ProfitMarginRowDto::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = rows.stream().map(ProfitMarginRowDto::getEstimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProfit = rows.stream().map(ProfitMarginRowDto::getGrossProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalUnits = rows.stream().mapToLong(row -> row.getQuantitySold() != null ? row.getQuantitySold() : 0L).sum();

        return ProfitMarginReportDto.builder()
                .kpis(buildKpis(totalRevenue, totalCost, totalProfit, rows.size(), totalUnits, sales.size()))
                .manufacturerProfitSplit(buildManufacturerSplit(rows))
                .topProfitProducts(buildTopProfitProducts(rows))
                .rows(rows)
                .build();
    }

    private List<Sale> findCompletedSales(Shop shop, LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveFrom = fromDate;
        LocalDate effectiveTo = toDate;

        if (effectiveFrom == null && effectiveTo == null) {
            effectiveFrom = LocalDate.now().withDayOfMonth(1);
            effectiveTo = LocalDate.now();
        } else if (effectiveFrom == null) {
            effectiveFrom = effectiveTo;
        } else if (effectiveTo == null) {
            effectiveTo = effectiveFrom;
        }

        return saleRepository.findByShopAndSaleDateBetweenOrderBySaleDateDesc(
                        shop,
                        effectiveFrom.atStartOfDay(),
                        effectiveTo.plusDays(1).atStartOfDay())
                .stream()
                .filter(sale -> sale.getStatus() == null || sale.getStatus() == SaleStatus.COMPLETED)
                .toList();
    }

    private boolean matchesFilter(SaleItem item, String keyword, String manufacturer) {
        String productName = item.getProduct().getName();
        String genericName = item.getProduct().getGenericName();
        String barcode = item.getProduct().getBarcode();
        String itemManufacturer = item.getProduct().getManufacturer();

        boolean keywordMatch = keyword == null || keyword.isBlank()
                || contains(productName, keyword)
                || contains(genericName, keyword)
                || contains(barcode, keyword)
                || contains(itemManufacturer, keyword);

        boolean manufacturerMatch = manufacturer == null || manufacturer.isBlank()
                || contains(itemManufacturer, manufacturer);

        return keywordMatch && manufacturerMatch;
    }

    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private BigDecimal estimateCost(SaleItem item, List<SaleItemBatchAllocation> allocations) {
        if (allocations != null && !allocations.isEmpty()) {
            return allocations.stream()
                    .map(allocation -> batchCost(allocation.getPurchaseBatch(), allocation.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal fallbackPurchasePrice = item.getProduct() != null ? safe(item.getProduct().getPurchasePrice()) : BigDecimal.ZERO;
        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
        return fallbackPurchasePrice.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal batchCost(PurchaseBatch batch, Integer quantity) {
        BigDecimal purchasePrice = batch != null ? safe(batch.getPurchasePrice()) : BigDecimal.ZERO;
        int qty = quantity != null ? quantity : 0;
        return purchasePrice.multiply(BigDecimal.valueOf(qty));
    }

    private ProfitMarginRowDto toRow(ProfitAccumulator accumulator) {
        BigDecimal grossProfit = accumulator.revenue.subtract(accumulator.estimatedCost);
        Double marginPercent = accumulator.revenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100))
                        .divide(accumulator.revenue, 2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        return ProfitMarginRowDto.builder()
                .productId(accumulator.productId)
                .productName(accumulator.productName)
                .manufacturer(accumulator.manufacturer != null ? accumulator.manufacturer : "Not set")
                .quantitySold(accumulator.quantitySold)
                .revenue(accumulator.revenue)
                .estimatedCost(accumulator.estimatedCost)
                .grossProfit(grossProfit)
                .marginPercent(marginPercent)
                .lastSoldAt(accumulator.lastSoldAt)
                .build();
    }

    private List<InsightsSummaryCardDto> buildKpis(BigDecimal totalRevenue,
                                                   BigDecimal totalCost,
                                                   BigDecimal totalProfit,
                                                   int skuCount,
                                                   long totalUnits,
                                                   int invoiceCount) {
        BigDecimal marginPercent = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return List.of(
                new InsightsSummaryCardDto("Revenue", money(totalRevenue), "Completed sales revenue for the selected range"),
                new InsightsSummaryCardDto("Estimated Cost", money(totalCost), "Cost estimated from sold batches or product purchase price"),
                new InsightsSummaryCardDto("Gross Profit", money(totalProfit), "Revenue minus estimated sold cost"),
                new InsightsSummaryCardDto("Margin %", marginPercent.stripTrailingZeros().toPlainString() + "%", "Overall gross margin in the selected range"),
                new InsightsSummaryCardDto("Profitable SKUs", String.valueOf(skuCount), "Products contributing to this margin view"),
                new InsightsSummaryCardDto("Units Sold", String.valueOf(totalUnits), "Total medicine units covered by the report")
        );
    }

    private List<PaymentModeSummaryDto> buildManufacturerSplit(List<ProfitMarginRowDto> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        ProfitMarginRowDto::getManufacturer,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                ProfitMarginRowDto::getGrossProfit,
                                BigDecimal::add)))
                .entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(6)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    private List<TopProductSummaryDto> buildTopProfitProducts(List<ProfitMarginRowDto> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(ProfitMarginRowDto::getGrossProfit).reversed())
                .limit(6)
                .map(row -> new TopProductSummaryDto(row.getProductName(), row.getGrossProfit().doubleValue()))
                .toList();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String money(BigDecimal value) {
        return "₹" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static class ProfitAccumulator {
        private final Long productId;
        private final String productName;
        private final String manufacturer;
        private long quantitySold = 0L;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal estimatedCost = BigDecimal.ZERO;
        private LocalDateTime lastSoldAt;

        private ProfitAccumulator(Long productId, String productName, String manufacturer) {
            this.productId = productId;
            this.productName = productName;
            this.manufacturer = manufacturer;
        }
    }
}
