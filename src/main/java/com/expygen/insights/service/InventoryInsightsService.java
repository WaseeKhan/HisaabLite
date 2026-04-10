package com.expygen.insights.service;

import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.CurrentStockRowDto;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.NearExpiryRowDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryInsightsService {

    private final ProductRepository productRepository;
    private final PurchaseBatchRepository purchaseBatchRepository;

    public List<PurchaseBatch> findCurrentStockBatches(Shop shop, String keyword, String stockStatus, String expiryRange, String manufacturer) {
        LocalDate today = LocalDate.now();
        return purchaseBatchRepository.findByShopAndActiveTrueOrderByCreatedAtDesc(shop, PageRequest.of(0, 1000)).getContent().stream()
                .filter(batch -> keyword == null || keyword.isBlank() || matchesBatchKeyword(batch, keyword))
                .filter(batch -> manufacturer == null || manufacturer.isBlank()
                        || contains(batch.getProduct() != null ? batch.getProduct().getManufacturer() : null, manufacturer))
                .filter(batch -> matchesStockStatus(batch, stockStatus, today))
                .filter(batch -> matchesExpiryRange(batch, expiryRange, today))
                .sorted(Comparator.comparing((PurchaseBatch batch) -> batch.getProduct() != null ? safeString(batch.getProduct().getName()) : "")
                        .thenComparing(PurchaseBatch::getBatchNumber, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public List<CurrentStockRowDto> buildCurrentStockRows(List<PurchaseBatch> batches) {
        return batches.stream()
                .map(batch -> new CurrentStockRowDto(
                        batch.getId(),
                        batch.getProduct() != null ? safeString(batch.getProduct().getName()) : "Unknown Product",
                        safeString(batch.getBatchNumber()),
                        batch.getProduct() != null ? safeString(batch.getProduct().getManufacturer()) : "",
                        batch.getProduct() != null ? safeString(batch.getProduct().getBarcode()) : "",
                        safeInt(batch.getAvailableQuantity()),
                        safe(batch.getMrp()),
                        safe(batch.getSalePrice()),
                        batch.getExpiryDate(),
                        round(safe(batch.getSalePrice()) * safeInt(batch.getAvailableQuantity())),
                        resolveStockStatus(batch, LocalDate.now())
                ))
                .toList();
    }

    public List<NearExpiryRowDto> buildNearExpiryRows(List<PurchaseBatch> batches) {
        LocalDate today = LocalDate.now();
        return batches.stream()
                .filter(batch -> batch.getExpiryDate() != null)
                .map(batch -> new NearExpiryRowDto(
                        batch.getId(),
                        batch.getProduct() != null ? safeString(batch.getProduct().getName()) : "Unknown Product",
                        safeString(batch.getBatchNumber()),
                        batch.getProduct() != null ? safeString(batch.getProduct().getManufacturer()) : "",
                        safeInt(batch.getAvailableQuantity()),
                        batch.getExpiryDate(),
                        ChronoUnit.DAYS.between(today, batch.getExpiryDate()),
                        safe(batch.getMrp()),
                        round(safe(batch.getMrp()) * safeInt(batch.getAvailableQuantity())),
                        resolveExpiryStatus(batch, today)
                ))
                .sorted(Comparator.comparing(NearExpiryRowDto::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<InsightsSummaryCardDto> buildCurrentStockKpis(Shop shop, List<PurchaseBatch> batches) {
        LocalDate today = LocalDate.now();
        long totalProducts = batches.stream().map(batch -> batch.getProduct() != null ? batch.getProduct().getId() : null).filter(id -> id != null).distinct().count();
        long totalBatches = batches.size();
        double stockValue = batches.stream().mapToDouble(batch -> safe(batch.getSalePrice()) * safeInt(batch.getAvailableQuantity())).sum();
        long lowStock = productRepository.findLowStockProducts(shop).size();
        long outOfStock = productRepository.findByShopAndActiveTrue(shop).stream().filter(p -> safeInt(p.getStockQuantity()) <= 0).count();
        long nearExpiry = batches.stream().filter(batch -> batch.getExpiryDate() != null
                && !batch.getExpiryDate().isBefore(today)
                && !batch.getExpiryDate().isAfter(today.plusDays(90))).count();

        return List.of(
                card("Total Products", String.valueOf(totalProducts), "Visible products with live inventory presence"),
                card("Total Batches", String.valueOf(totalBatches), "Tracked purchase batches in active stock"),
                card("Stock Value", money(stockValue), "Estimated inventory value in current stock"),
                card("Low Stock Items", String.valueOf(lowStock), "Needs replenishment attention"),
                card("Out of Stock", String.valueOf(outOfStock), "Items currently unavailable for sale"),
                card("Near Expiry in Stock", String.valueOf(nearExpiry), "Stock requiring expiry review")
        );
    }

    public List<InsightsSummaryCardDto> buildNearExpiryKpis(List<PurchaseBatch> batches) {
        LocalDate today = LocalDate.now();
        long expired = batches.stream().filter(batch -> batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)).count();
        long thirty = batches.stream().filter(batch -> withinDays(batch, today, 30)).count();
        long sixty = batches.stream().filter(batch -> withinDays(batch, today, 60)).count();
        long ninety = batches.stream().filter(batch -> withinDays(batch, today, 90)).count();
        double riskValue = batches.stream().mapToDouble(batch -> safe(batch.getMrp()) * safeInt(batch.getAvailableQuantity())).sum();
        long products = batches.stream().map(batch -> batch.getProduct() != null ? batch.getProduct().getId() : null).filter(id -> id != null).distinct().count();

        return List.of(
                card("Expired Batches", String.valueOf(expired), "Immediate clearance or adjustment needed"),
                card("Within 30 Days", String.valueOf(thirty), "High urgency inventory exposure"),
                card("Within 60 Days", String.valueOf(sixty), "Upcoming expiry review bucket"),
                card("Within 90 Days", String.valueOf(ninety), "Early watchlist for stock ageing"),
                card("At-Risk Stock Value", money(riskValue), "Estimated stock value under expiry pressure"),
                card("Affected Products", String.valueOf(products), "Distinct products with expiry exposure")
        );
    }

    public List<PaymentModeSummaryDto> buildStockHealthDistribution(List<PurchaseBatch> batches) {
        LocalDate today = LocalDate.now();
        Map<String, Long> grouped = batches.stream()
                .collect(Collectors.groupingBy(batch -> resolveStockStatus(batch, today), Collectors.counting()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    public List<PaymentModeSummaryDto> buildManufacturerStock(List<PurchaseBatch> batches) {
        return batches.stream()
                .collect(Collectors.groupingBy(
                        batch -> batch.getProduct() != null ? safeString(batch.getProduct().getManufacturer(), "Unknown") : "Unknown",
                        Collectors.summingDouble(batch -> safeInt(batch.getAvailableQuantity()))))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), round(entry.getValue())))
                .toList();
    }

    public List<PaymentModeSummaryDto> buildExpiryBucketCounts(List<PurchaseBatch> batches) {
        LocalDate today = LocalDate.now();
        return List.of(
                new PaymentModeSummaryDto("Expired", (double) batches.stream().filter(batch -> batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)).count()),
                new PaymentModeSummaryDto("0-30 Days", (double) batches.stream().filter(batch -> withinDays(batch, today, 30)).count()),
                new PaymentModeSummaryDto("31-60 Days", (double) batches.stream().filter(batch -> betweenDays(batch, today, 31, 60)).count()),
                new PaymentModeSummaryDto("61-90 Days", (double) batches.stream().filter(batch -> betweenDays(batch, today, 61, 90)).count())
        );
    }

    public List<PaymentModeSummaryDto> buildExpiryValueSplit(List<PurchaseBatch> batches) {
        LocalDate today = LocalDate.now();
        return List.of(
                new PaymentModeSummaryDto("Expired", round(valueFor(batches.stream().filter(batch -> batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)).toList()))),
                new PaymentModeSummaryDto("0-30 Days", round(valueFor(batches.stream().filter(batch -> withinDays(batch, today, 30)).toList()))),
                new PaymentModeSummaryDto("31-60 Days", round(valueFor(batches.stream().filter(batch -> betweenDays(batch, today, 31, 60)).toList()))),
                new PaymentModeSummaryDto("61-90 Days", round(valueFor(batches.stream().filter(batch -> betweenDays(batch, today, 61, 90)).toList())))
        );
    }

    public List<PaymentModeSummaryDto> buildManufacturerExpiryRisk(List<PurchaseBatch> batches) {
        return batches.stream()
                .collect(Collectors.groupingBy(
                        batch -> batch.getProduct() != null ? safeString(batch.getProduct().getManufacturer(), "Unknown") : "Unknown",
                        Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    private boolean matchesBatchKeyword(PurchaseBatch batch, String keyword) {
        return contains(batch.getBatchNumber(), keyword)
                || contains(batch.getProduct() != null ? batch.getProduct().getName() : null, keyword)
                || contains(batch.getProduct() != null ? batch.getProduct().getBarcode() : null, keyword)
                || contains(batch.getProduct() != null ? batch.getProduct().getManufacturer() : null, keyword);
    }

    private boolean matchesStockStatus(PurchaseBatch batch, String stockStatus, LocalDate today) {
        if (stockStatus == null || stockStatus.isBlank()) return true;
        return resolveStockStatus(batch, today).equalsIgnoreCase(stockStatus.replace('_', ' '));
    }

    private boolean matchesExpiryRange(PurchaseBatch batch, String expiryRange, LocalDate today) {
        if (expiryRange == null || expiryRange.isBlank()) return true;
        if ("EXPIRED".equalsIgnoreCase(expiryRange)) {
            return batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today);
        }
        try {
            int days = Integer.parseInt(expiryRange);
            return batch.getExpiryDate() != null
                    && !batch.getExpiryDate().isBefore(today)
                    && !batch.getExpiryDate().isAfter(today.plusDays(days));
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private boolean withinDays(PurchaseBatch batch, LocalDate today, int days) {
        return batch.getExpiryDate() != null
                && !batch.getExpiryDate().isBefore(today)
                && !batch.getExpiryDate().isAfter(today.plusDays(days));
    }

    private boolean betweenDays(PurchaseBatch batch, LocalDate today, int from, int to) {
        return batch.getExpiryDate() != null
                && batch.getExpiryDate().isAfter(today.plusDays(from - 1L))
                && !batch.getExpiryDate().isAfter(today.plusDays(to));
    }

    private double valueFor(List<PurchaseBatch> batches) {
        return batches.stream().mapToDouble(batch -> safe(batch.getMrp()) * safeInt(batch.getAvailableQuantity())).sum();
    }

    private String resolveStockStatus(PurchaseBatch batch, LocalDate today) {
        int available = safeInt(batch.getAvailableQuantity());
        if (available <= 0) return "Out of Stock";
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today.plusDays(90))) return "Near Expiry";
        Product product = batch.getProduct();
        if (product != null && available <= safeInt(product.getMinStock())) return "Low Stock";
        return "In Stock";
    }

    private String resolveExpiryStatus(PurchaseBatch batch, LocalDate today) {
        if (batch.getExpiryDate() == null) return "No Expiry";
        if (batch.getExpiryDate().isBefore(today)) return "Expired";
        long days = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
        if (days <= 30) return "Within 30 Days";
        if (days <= 60) return "Within 60 Days";
        return "Within 90 Days";
    }

    private InsightsSummaryCardDto card(String label, String value, String description) {
        return new InsightsSummaryCardDto(label, value, description);
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value != null && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
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
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String money(double value) {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", round(value));
    }
}
