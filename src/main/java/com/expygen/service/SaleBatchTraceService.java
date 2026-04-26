package com.expygen.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expygen.dto.SaleBatchTraceSummaryDTO;
import com.expygen.dto.SoldBatchTraceDTO;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleItemBatchAllocation;
import com.expygen.repository.SaleItemBatchAllocationRepository;
import com.expygen.repository.SaleItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SaleBatchTraceService {

    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;

    @Transactional(readOnly = true)
    public Map<Long, List<SoldBatchTraceDTO>> getBatchTraceBySaleItem(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<SoldBatchTraceDTO>> traceBySaleItemId = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (SaleItem item : items) {
            traceBySaleItemId.put(item.getId(), new ArrayList<>());
        }

        for (SaleItemBatchAllocation allocation : saleItemBatchAllocationRepository.findBySaleItemIn(items)) {
            PurchaseBatch purchaseBatch = allocation.getPurchaseBatch();
            if (purchaseBatch == null || allocation.getSaleItem() == null) {
                continue;
            }

            LocalDate expiryDate = purchaseBatch.getExpiryDate();
            traceBySaleItemId.computeIfAbsent(allocation.getSaleItem().getId(), key -> new ArrayList<>())
                    .add(SoldBatchTraceDTO.builder()
                            .batchNumber(resolveBatchNumber(purchaseBatch))
                            .quantity(allocation.getQuantity())
                            .expiryDate(expiryDate)
                            .expired(expiryDate != null && expiryDate.isBefore(today))
                            .build());
        }

        traceBySaleItemId.values().forEach(list -> list.sort(
                Comparator.comparing(SoldBatchTraceDTO::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SoldBatchTraceDTO::getBatchNumber, Comparator.nullsLast(String::compareToIgnoreCase))));

        return traceBySaleItemId;
    }

    @Transactional(readOnly = true)
    public Map<Long, SaleBatchTraceSummaryDTO> summarizeSales(List<Sale> sales) {
        if (sales == null || sales.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, SaleBatchTraceSummaryDTO> summaryBySaleId = new HashMap<>();
        for (Sale sale : sales) {
            summaryBySaleId.put(sale.getId(), SaleBatchTraceSummaryDTO.builder()
                    .batchManaged(false)
                    .tracedBatchCount(0)
                    .tracedUnits(0)
                    .expiredBatchCount(0)
                    .build());
        }

        List<SaleItem> saleItems = saleItemRepository.findBySaleIn(sales);
        if (saleItems.isEmpty()) {
            return summaryBySaleId;
        }

        Map<Long, Long> saleIdBySaleItemId = new HashMap<>();
        for (SaleItem saleItem : saleItems) {
            if (saleItem.getSale() != null) {
                saleIdBySaleItemId.put(saleItem.getId(), saleItem.getSale().getId());
            }
        }

        LocalDate today = LocalDate.now();
        Map<Long, Set<Long>> uniqueBatchIdsBySaleId = new HashMap<>();
        Map<Long, Set<Long>> expiredBatchIdsBySaleId = new HashMap<>();
        Map<Long, LocalDate> earliestExpiryBySaleId = new HashMap<>();

        for (SaleItemBatchAllocation allocation : saleItemBatchAllocationRepository.findBySaleItemIn(saleItems)) {
            if (allocation.getSaleItem() == null || allocation.getPurchaseBatch() == null) {
                continue;
            }

            Long saleId = saleIdBySaleItemId.get(allocation.getSaleItem().getId());
            if (saleId == null) {
                continue;
            }

            PurchaseBatch purchaseBatch = allocation.getPurchaseBatch();
            SaleBatchTraceSummaryDTO summary = summaryBySaleId.computeIfAbsent(saleId, key -> SaleBatchTraceSummaryDTO.builder().build());
            summary.setBatchManaged(true);
            summary.setTracedUnits(summary.getTracedUnits() + allocation.getQuantity());

            uniqueBatchIdsBySaleId.computeIfAbsent(saleId, key -> new LinkedHashSet<>()).add(purchaseBatch.getId());

            LocalDate expiryDate = purchaseBatch.getExpiryDate();
            if (expiryDate != null) {
                LocalDate currentEarliest = earliestExpiryBySaleId.get(saleId);
                if (currentEarliest == null || expiryDate.isBefore(currentEarliest)) {
                    earliestExpiryBySaleId.put(saleId, expiryDate);
                }
                if (expiryDate.isBefore(today)) {
                    expiredBatchIdsBySaleId.computeIfAbsent(saleId, key -> new LinkedHashSet<>()).add(purchaseBatch.getId());
                }
            }
        }

        summaryBySaleId.forEach((saleId, summary) -> {
            summary.setTracedBatchCount(uniqueBatchIdsBySaleId.getOrDefault(saleId, Collections.emptySet()).size());
            summary.setExpiredBatchCount(expiredBatchIdsBySaleId.getOrDefault(saleId, Collections.emptySet()).size());
            summary.setNextExpiryDate(earliestExpiryBySaleId.get(saleId));
        });

        return summaryBySaleId;
    }

    private String resolveBatchNumber(PurchaseBatch purchaseBatch) {
        String batchNumber = purchaseBatch.getBatchNumber();
        if (batchNumber == null || batchNumber.isBlank()) {
            return "Batch " + Objects.requireNonNullElse(purchaseBatch.getId(), "—");
        }
        return batchNumber;
    }
}
