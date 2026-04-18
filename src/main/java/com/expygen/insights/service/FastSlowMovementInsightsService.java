package com.expygen.insights.service;

import com.expygen.entity.Product;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.FastSlowMovementReportDto;
import com.expygen.insights.dto.FastSlowMovementRowDto;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.insights.dto.TopProductSummaryDto;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FastSlowMovementInsightsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    public FastSlowMovementReportDto buildReport(Shop shop,
                                                 LocalDate fromDate,
                                                 LocalDate toDate,
                                                 String movementStatus,
                                                 String keyword,
                                                 String manufacturer) {
        LocalDate effectiveFrom = fromDate;
        LocalDate effectiveTo = toDate;

        if (effectiveFrom == null && effectiveTo == null) {
            effectiveTo = LocalDate.now();
            effectiveFrom = effectiveTo.minusDays(29);
        } else if (effectiveFrom == null) {
            effectiveFrom = effectiveTo;
        } else if (effectiveTo == null) {
            effectiveTo = effectiveFrom;
        }

        long periodDays = Math.max(1, ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1);

        List<Sale> completedSales = saleRepository.findByShopAndSaleDateBetweenOrderBySaleDateDesc(
                        shop,
                        effectiveFrom.atStartOfDay(),
                        effectiveTo.plusDays(1).atStartOfDay())
                .stream()
                .filter(sale -> sale.getStatus() == null || sale.getStatus() == SaleStatus.COMPLETED)
                .toList();

        List<SaleItem> saleItems = completedSales.isEmpty() ? List.of() : saleItemRepository.findBySaleIn(completedSales);
        List<Product> activeProducts = productRepository.findByShopAndActiveTrue(shop);

        Map<Long, LocalDateTime> lastSoldByProductId = saleItemRepository.findLastSoldAtByProduct(shop).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> (LocalDateTime) row[1]
                ));

        Map<Long, MovementAccumulator> byProduct = new LinkedHashMap<>();

        for (Product product : activeProducts) {
            if (product == null || product.getId() == null) {
                continue;
            }
            byProduct.put(product.getId(), MovementAccumulator.forProduct(product));
        }

        for (SaleItem item : saleItems) {
            if (item.getProduct() == null || item.getProduct().getId() == null) {
                continue;
            }
            byProduct.computeIfAbsent(item.getProduct().getId(), ignored -> MovementAccumulator.forProduct(item.getProduct()))
                    .add(item);
        }

        List<FastSlowMovementRowDto> rows = byProduct.values().stream()
                .map(accumulator -> toRow(accumulator, periodDays, lastSoldByProductId.get(accumulator.productId)))
                .filter(row -> keyword == null || keyword.isBlank() || matchesKeyword(row, keyword))
                .filter(row -> manufacturer == null || manufacturer.isBlank() || contains(row.getManufacturer(), manufacturer))
                .filter(row -> movementStatus == null || movementStatus.isBlank() || "ALL".equalsIgnoreCase(movementStatus)
                        || row.getMovementStatus().equalsIgnoreCase(movementStatus))
                .sorted(Comparator
                        .comparingInt((FastSlowMovementRowDto row) -> statusRank(row.getMovementStatus()))
                        .thenComparing(FastSlowMovementRowDto::getUnitsSold, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FastSlowMovementRowDto::getProductName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return FastSlowMovementReportDto.builder()
                .kpis(buildKpis(rows, periodDays))
                .movementStatusSplit(buildMovementStatusSplit(rows))
                .manufacturerUnitsSplit(buildManufacturerUnitsSplit(rows))
                .topMovers(buildTopMovers(rows))
                .rows(rows)
                .build();
    }

    private FastSlowMovementRowDto toRow(MovementAccumulator accumulator, long periodDays, LocalDateTime lastSoldAt) {
        double avgDailyUnits = accumulator.unitsSold > 0 ? round((double) accumulator.unitsSold / periodDays) : 0.0;
        double stockCoverDays = avgDailyUnits > 0 ? round(accumulator.currentStock / avgDailyUnits) : 0.0;

        return FastSlowMovementRowDto.builder()
                .productId(accumulator.productId)
                .productName(safeString(accumulator.productName, "Unknown Product"))
                .manufacturer(safeString(accumulator.manufacturer, "Unknown"))
                .barcode(safeString(accumulator.barcode))
                .currentStock(accumulator.currentStock)
                .unitsSold(accumulator.unitsSold)
                .invoiceCount(accumulator.invoiceIds.size() * 1L)
                .revenue(round(accumulator.revenue.doubleValue()))
                .avgDailyUnits(avgDailyUnits)
                .lastSoldAt(lastSoldAt)
                .stockCoverDays(stockCoverDays > 0 ? stockCoverDays : null)
                .movementStatus(classifyMovement(accumulator.unitsSold, avgDailyUnits))
                .build();
    }

    private List<InsightsSummaryCardDto> buildKpis(List<FastSlowMovementRowDto> rows, long periodDays) {
        long fastMovers = rows.stream().filter(row -> "Fast".equals(row.getMovementStatus())).count();
        long slowMovers = rows.stream().filter(row -> "Slow".equals(row.getMovementStatus())).count();
        long noMovement = rows.stream().filter(row -> "No Movement".equals(row.getMovementStatus())).count();
        long unitsSold = rows.stream().mapToLong(row -> row.getUnitsSold() != null ? row.getUnitsSold() : 0L).sum();
        double revenue = rows.stream().mapToDouble(row -> row.getRevenue() != null ? row.getRevenue() : 0.0).sum();
        double avgDailyUnits = periodDays > 0 ? round((double) unitsSold / periodDays) : 0.0;

        return List.of(
                card("Fast Movers", String.valueOf(fastMovers), "Products moving strongly and likely needing regular reorder"),
                card("Slow Movers", String.valueOf(slowMovers), "Products selling, but slowly enough to deserve purchase caution"),
                card("No Movement", String.valueOf(noMovement), "Products with visible stock but no sales in the selected period"),
                card("Units Sold", String.valueOf(unitsSold), "Total units sold across the selected movement window"),
                card("Revenue", money(revenue), "Sales value produced by the movement window"),
                card("Avg Daily Units", String.format(Locale.ENGLISH, "%.2f", avgDailyUnits), "Average unit movement per day across the selected range")
        );
    }

    private List<PaymentModeSummaryDto> buildMovementStatusSplit(List<FastSlowMovementRowDto> rows) {
        return List.of(
                bucket("Fast", rows, "Fast"),
                bucket("Moderate", rows, "Moderate"),
                bucket("Slow", rows, "Slow"),
                bucket("No Movement", rows, "No Movement")
        );
    }

    private List<PaymentModeSummaryDto> buildManufacturerUnitsSplit(List<FastSlowMovementRowDto> rows) {
        return rows.stream()
                .filter(row -> row.getUnitsSold() != null && row.getUnitsSold() > 0)
                .collect(Collectors.groupingBy(
                        row -> safeString(row.getManufacturer(), "Unknown"),
                        Collectors.summingDouble(row -> row.getUnitsSold() != null ? row.getUnitsSold() : 0L)))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), round(entry.getValue())))
                .toList();
    }

    private List<TopProductSummaryDto> buildTopMovers(List<FastSlowMovementRowDto> rows) {
        return rows.stream()
                .filter(row -> row.getUnitsSold() != null && row.getUnitsSold() > 0)
                .sorted(Comparator.comparing(FastSlowMovementRowDto::getUnitsSold).reversed())
                .limit(6)
                .map(row -> new TopProductSummaryDto(row.getProductName(), row.getUnitsSold().doubleValue()))
                .toList();
    }

    private PaymentModeSummaryDto bucket(String label, List<FastSlowMovementRowDto> rows, String status) {
        double count = rows.stream().filter(row -> status.equals(row.getMovementStatus())).count();
        return new PaymentModeSummaryDto(label, count);
    }

    private String classifyMovement(long unitsSold, double avgDailyUnits) {
        if (unitsSold <= 0) {
            return "No Movement";
        }
        if (avgDailyUnits >= 1.0 || unitsSold >= 25) {
            return "Fast";
        }
        if (avgDailyUnits >= 0.35 || unitsSold >= 8) {
            return "Moderate";
        }
        return "Slow";
    }

    private boolean matchesKeyword(FastSlowMovementRowDto row, String keyword) {
        return contains(row.getProductName(), keyword)
                || contains(row.getManufacturer(), keyword)
                || contains(row.getBarcode(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value != null
                && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private int statusRank(String status) {
        if ("Fast".equals(status)) return 0;
        if ("Moderate".equals(status)) return 1;
        if ("Slow".equals(status)) return 2;
        return 3;
    }

    private InsightsSummaryCardDto card(String label, String value, String description) {
        return new InsightsSummaryCardDto(label, value, description);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String safeString(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String money(double value) {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", round(value));
    }

    private static class MovementAccumulator {
        private final Long productId;
        private final String productName;
        private final String manufacturer;
        private final String barcode;
        private final int currentStock;
        private long unitsSold = 0L;
        private BigDecimal revenue = BigDecimal.ZERO;
        private final List<Long> invoiceIds = new ArrayList<>();

        private MovementAccumulator(Long productId, String productName, String manufacturer, String barcode, int currentStock) {
            this.productId = productId;
            this.productName = productName;
            this.manufacturer = manufacturer;
            this.barcode = barcode;
            this.currentStock = currentStock;
        }

        private static MovementAccumulator forProduct(Product product) {
            return new MovementAccumulator(
                    product.getId(),
                    product.getName(),
                    product.getManufacturer(),
                    product.getBarcode(),
                    product.getStockQuantity() != null ? product.getStockQuantity() : 0
            );
        }

        private void add(SaleItem item) {
            this.unitsSold += item.getQuantity() != null ? item.getQuantity() : 0;
            this.revenue = this.revenue.add(item.getTotalWithGst() != null ? item.getTotalWithGst() : BigDecimal.ZERO);
            if (item.getSale() != null && item.getSale().getId() != null && !invoiceIds.contains(item.getSale().getId())) {
                invoiceIds.add(item.getSale().getId());
            }
        }
    }
}
