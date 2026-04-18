package com.expygen.insights.service;

import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.ExpiryLossReportDto;
import com.expygen.insights.dto.ExpiryLossRowDto;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.repository.PurchaseBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpiryLossInsightsService {

    private final PurchaseBatchRepository purchaseBatchRepository;

    public ExpiryLossReportDto buildReport(Shop shop, String expiryBucket, String keyword, String manufacturer) {
        LocalDate today = LocalDate.now();
        String effectiveBucket = (expiryBucket == null || expiryBucket.isBlank()) ? "90" : expiryBucket;

        List<PurchaseBatch> filtered = purchaseBatchRepository.findByShopAndActiveTrueOrderByCreatedAtDesc(shop, PageRequest.of(0, 1000))
                .getContent().stream()
                .filter(batch -> safeInt(batch.getAvailableQuantity()) > 0)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> matchesBucket(batch, effectiveBucket, today))
                .filter(batch -> keyword == null || keyword.isBlank() || matchesKeyword(batch, keyword))
                .filter(batch -> manufacturer == null || manufacturer.isBlank()
                        || contains(batch.getProduct() != null ? batch.getProduct().getManufacturer() : null, manufacturer))
                .toList();

        List<ExpiryLossRowDto> rows = filtered.stream()
                .map(batch -> toRow(batch, today))
                .sorted((a, b) -> {
                    if (a.getDaysFromToday() == null && b.getDaysFromToday() == null) return 0;
                    if (a.getDaysFromToday() == null) return 1;
                    if (b.getDaysFromToday() == null) return -1;
                    return Long.compare(a.getDaysFromToday(), b.getDaysFromToday());
                })
                .toList();

        return ExpiryLossReportDto.builder()
                .kpis(buildKpis(rows))
                .bucketValueSplit(buildBucketValueSplit(rows))
                .manufacturerLossSplit(buildManufacturerLossSplit(rows))
                .rows(rows)
                .build();
    }

    private ExpiryLossRowDto toRow(PurchaseBatch batch, LocalDate today) {
        long daysFromToday = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
        int qty = safeInt(batch.getAvailableQuantity());
        double purchasePrice = safe(batch.getPurchasePrice());
        double mrp = safe(batch.getMrp());
        double salePrice = safe(batch.getSalePrice());
        double estimatedCostLoss = round((purchasePrice > 0 ? purchasePrice : salePrice) * qty);
        double retailValueAtRisk = round((mrp > 0 ? mrp : salePrice) * qty);

        return ExpiryLossRowDto.builder()
                .batchId(batch.getId())
                .productName(batch.getProduct() != null ? safeString(batch.getProduct().getName(), "Unknown Product") : "Unknown Product")
                .batchNumber(safeString(batch.getBatchNumber()))
                .manufacturer(batch.getProduct() != null ? safeString(batch.getProduct().getManufacturer(), "Unknown") : "Unknown")
                .availableQty(qty)
                .expiryDate(batch.getExpiryDate())
                .daysFromToday(daysFromToday)
                .purchasePrice(round(purchasePrice))
                .mrp(round(mrp))
                .salePrice(round(salePrice))
                .estimatedCostLoss(estimatedCostLoss)
                .retailValueAtRisk(retailValueAtRisk)
                .status(resolveStatus(daysFromToday))
                .build();
    }

    private List<InsightsSummaryCardDto> buildKpis(List<ExpiryLossRowDto> rows) {
        long expired = rows.stream().filter(row -> "Expired".equals(row.getStatus())).count();
        long within30 = rows.stream().filter(row -> "Within 30 Days".equals(row.getStatus())).count();
        long within90 = rows.stream().filter(row -> !"Expired".equals(row.getStatus())).count();
        double costLoss = rows.stream().mapToDouble(ExpiryLossRowDto::getEstimatedCostLoss).sum();
        double retailRisk = rows.stream().mapToDouble(ExpiryLossRowDto::getRetailValueAtRisk).sum();
        long affectedProducts = rows.stream().map(ExpiryLossRowDto::getProductName).distinct().count();

        return List.of(
                card("Expired Batches", String.valueOf(expired), "Inventory already beyond expiry date"),
                card("Within 30 Days", String.valueOf(within30), "Immediate expiry pressure needing action"),
                card("Near Expiry Batches", String.valueOf(within90), "Batches under expiry watch in the selected window"),
                card("Estimated Cost Loss", money(costLoss), "Approximate cost value exposed to expiry loss"),
                card("Retail Value At Risk", money(retailRisk), "Approximate selling value currently under expiry pressure"),
                card("Affected Products", String.valueOf(affectedProducts), "Distinct medicines contributing to expiry loss risk")
        );
    }

    private List<PaymentModeSummaryDto> buildBucketValueSplit(List<ExpiryLossRowDto> rows) {
        return List.of(
                valueBucket("Expired", rows, row -> "Expired".equals(row.getStatus())),
                valueBucket("Within 30 Days", rows, row -> "Within 30 Days".equals(row.getStatus())),
                valueBucket("Within 60 Days", rows, row -> "Within 60 Days".equals(row.getStatus())),
                valueBucket("Within 90 Days", rows, row -> "Within 90 Days".equals(row.getStatus()))
        );
    }

    private List<PaymentModeSummaryDto> buildManufacturerLossSplit(List<ExpiryLossRowDto> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> safeString(row.getManufacturer(), "Unknown"),
                        Collectors.summingDouble(row -> row.getEstimatedCostLoss() != null ? row.getEstimatedCostLoss() : 0.0)))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), round(entry.getValue())))
                .toList();
    }

    private PaymentModeSummaryDto valueBucket(String label, List<ExpiryLossRowDto> rows, Predicate<ExpiryLossRowDto> matcher) {
        double value = rows.stream()
                .filter(matcher)
                .mapToDouble(row -> row.getEstimatedCostLoss() != null ? row.getEstimatedCostLoss() : 0.0)
                .sum();
        return new PaymentModeSummaryDto(label, round(value));
    }

    private boolean matchesBucket(PurchaseBatch batch, String bucket, LocalDate today) {
        return switch (bucket.toUpperCase(Locale.ENGLISH)) {
            case "EXPIRED" -> batch.getExpiryDate().isBefore(today);
            case "30" -> !batch.getExpiryDate().isBefore(today) && !batch.getExpiryDate().isAfter(today.plusDays(30));
            case "60" -> !batch.getExpiryDate().isBefore(today) && !batch.getExpiryDate().isAfter(today.plusDays(60));
            case "90" -> !batch.getExpiryDate().isBefore(today) && !batch.getExpiryDate().isAfter(today.plusDays(90));
            default -> true;
        };
    }

    private boolean matchesKeyword(PurchaseBatch batch, String keyword) {
        return contains(batch.getBatchNumber(), keyword)
                || contains(batch.getProduct() != null ? batch.getProduct().getName() : null, keyword)
                || contains(batch.getProduct() != null ? batch.getProduct().getBarcode() : null, keyword)
                || contains(batch.getProduct() != null ? batch.getProduct().getManufacturer() : null, keyword);
    }

    private String resolveStatus(long daysFromToday) {
        if (daysFromToday < 0) return "Expired";
        if (daysFromToday <= 30) return "Within 30 Days";
        if (daysFromToday <= 60) return "Within 60 Days";
        return "Within 90 Days";
    }

    private InsightsSummaryCardDto card(String label, String value, String description) {
        return new InsightsSummaryCardDto(label, value, description);
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value != null
                && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safe(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
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
}
