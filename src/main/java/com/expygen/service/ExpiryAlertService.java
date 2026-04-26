package com.expygen.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expygen.dto.BatchDashboardSummary;
import com.expygen.dto.ExpiryAlertSummary;
import com.expygen.dto.ExpiryReportBucket;
import com.expygen.dto.ExpiryReportItem;
import com.expygen.dto.ExpiringBatchSnapshot;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Shop;
import com.expygen.repository.PurchaseBatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpiryAlertService {

    private final PurchaseBatchRepository purchaseBatchRepository;

    @Transactional(readOnly = true)
    public ExpiryAlertSummary buildAlertSummary(Shop shop) {
        LocalDate today = LocalDate.now();

        long expiredBatchCount = purchaseBatchRepository.countExpiredBatches(shop, today);
        long expiringIn7DaysCount = purchaseBatchRepository.countExpiringBatchesBetween(shop, today, today.plusDays(7));
        long expiringIn30DaysCount = purchaseBatchRepository.countExpiringBatchesBetween(shop, today, today.plusDays(30));
        long expiringIn60DaysCount = purchaseBatchRepository.countExpiringBatchesBetween(shop, today, today.plusDays(60));
        long expiringIn90DaysCount = purchaseBatchRepository.countExpiringBatchesBetween(shop, today, today.plusDays(90));

        long expiredUnits = safeLong(purchaseBatchRepository.sumExpiredQuantity(shop, today));
        long expiringIn7DaysUnits = safeLong(purchaseBatchRepository.sumExpiringQuantityBetween(shop, today, today.plusDays(7)));
        long expiringIn30DaysUnits = safeLong(purchaseBatchRepository.sumExpiringQuantityBetween(shop, today, today.plusDays(30)));
        long expiringIn60DaysUnits = safeLong(purchaseBatchRepository.sumExpiringQuantityBetween(shop, today, today.plusDays(60)));
        long expiringIn90DaysUnits = safeLong(purchaseBatchRepository.sumExpiringQuantityBetween(shop, today, today.plusDays(90)));

        return ExpiryAlertSummary.builder()
                .criticalAlertCount(expiredBatchCount + expiringIn7DaysCount)
                .criticalAlertUnits(expiredUnits + expiringIn7DaysUnits)
                .expiredBatchCount(expiredBatchCount)
                .expiredUnits(expiredUnits)
                .expiringIn7DaysCount(expiringIn7DaysCount)
                .expiringIn7DaysUnits(expiringIn7DaysUnits)
                .expiringIn30DaysCount(expiringIn30DaysCount)
                .expiringIn30DaysUnits(expiringIn30DaysUnits)
                .expiringIn60DaysCount(expiringIn60DaysCount)
                .expiringIn60DaysUnits(expiringIn60DaysUnits)
                .expiringIn90DaysCount(expiringIn90DaysCount)
                .expiringIn90DaysUnits(expiringIn90DaysUnits)
                .build();
    }

    @Transactional(readOnly = true)
    public BatchDashboardSummary buildDashboardSummary(Shop shop, int expiringLimit) {
        LocalDate today = LocalDate.now();
        ExpiryAlertSummary summary = buildAlertSummary(shop);

        List<ExpiringBatchSnapshot> expiringBatches = purchaseBatchRepository
                .findBatchesExpiringBetween(shop, today, today.plusDays(30), PageRequest.of(0, expiringLimit))
                .stream()
                .map(batch -> ExpiringBatchSnapshot.builder()
                        .productId(batch.getProduct().getId())
                        .productName(batch.getProduct().getName())
                        .batchNumber(batch.getBatchNumber())
                        .expiryDate(batch.getExpiryDate())
                        .availableQuantity(batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : 0)
                        .build())
                .toList();

        return BatchDashboardSummary.builder()
                .batchManagedMedicines(purchaseBatchRepository.countDistinctProductsWithActiveBatches(shop))
                .liveBatchCount(safeLong(purchaseBatchRepository.sumAvailableQuantityByShop(shop)))
                .sellableBatchUnits(safeLong(purchaseBatchRepository.sumSellableQuantityByShop(shop, today)))
                .criticalExpiryBatchCount(summary.getCriticalAlertCount())
                .criticalExpiryUnits(summary.getCriticalAlertUnits())
                .nearExpiryBatchCount(summary.getExpiringIn30DaysCount())
                .expiredBatchCount(summary.getExpiredBatchCount())
                .expiringBatches(expiringBatches)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExpiryReportItem> buildReportItems(Shop shop, ExpiryReportBucket bucket, int limit) {
        LocalDate today = LocalDate.now();
        List<PurchaseBatch> rows;

        switch (bucket) {
            case EXPIRED -> rows = purchaseBatchRepository.findExpiredBatches(shop, today, PageRequest.of(0, limit));
            case DAYS_30 -> rows = purchaseBatchRepository.findBatchesExpiringBetween(shop, today, today.plusDays(30), PageRequest.of(0, limit));
            case DAYS_60 -> rows = purchaseBatchRepository.findBatchesExpiringBetween(shop, today, today.plusDays(60), PageRequest.of(0, limit));
            case DAYS_90 -> rows = purchaseBatchRepository.findBatchesExpiringBetween(shop, today, today.plusDays(90), PageRequest.of(0, limit));
            case CRITICAL -> rows = Stream.concat(
                            purchaseBatchRepository.findExpiredBatches(shop, today, PageRequest.of(0, limit)).stream(),
                            purchaseBatchRepository.findBatchesExpiringBetween(shop, today, today.plusDays(7), PageRequest.of(0, limit)).stream())
                    .sorted(Comparator.comparing(PurchaseBatch::getExpiryDate, Comparator.nullsLast(LocalDate::compareTo))
                            .thenComparing(PurchaseBatch::getId))
                    .limit(limit)
                    .toList();
            default -> rows = List.of();
        }

        return rows.stream()
                .map(batch -> toReportItem(batch, today))
                .toList();
    }

    private ExpiryReportItem toReportItem(PurchaseBatch batch, LocalDate today) {
        LocalDate expiryDate = batch.getExpiryDate();
        long daysToExpiry = expiryDate != null ? ChronoUnit.DAYS.between(today, expiryDate) : Long.MAX_VALUE;

        String alertLevel;
        String statusLabel;
        if (expiryDate != null && expiryDate.isBefore(today)) {
            alertLevel = "expired";
            statusLabel = "Expired";
        } else if (expiryDate != null && ChronoUnit.DAYS.between(today, expiryDate) <= 7) {
            alertLevel = "critical";
            statusLabel = daysToExpiry == 0 ? "Expires today" : "Expires in " + daysToExpiry + " day" + (daysToExpiry == 1 ? "" : "s");
        } else if (expiryDate != null && ChronoUnit.DAYS.between(today, expiryDate) <= 30) {
            alertLevel = "warning";
            statusLabel = "Near expiry";
        } else {
            alertLevel = "stable";
            statusLabel = "Tracked";
        }

        return ExpiryReportItem.builder()
                .batchId(batch.getId())
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .genericName(batch.getProduct().getGenericName())
                .manufacturer(batch.getProduct().getManufacturer())
                .supplierName(batch.getPurchaseEntry().getSupplierName())
                .supplierInvoiceNumber(batch.getPurchaseEntry().getSupplierInvoiceNumber())
                .batchNumber(batch.getBatchNumber())
                .expiryDate(expiryDate)
                .daysToExpiry(daysToExpiry)
                .availableQuantity(batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : 0)
                .purchasePrice(batch.getPurchasePrice())
                .salePrice(batch.getSalePrice())
                .mrp(batch.getMrp())
                .statusLabel(statusLabel)
                .alertLevel(alertLevel)
                .build();
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }
}
