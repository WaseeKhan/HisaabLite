package com.hisaablite.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hisaablite.dto.BatchDashboardSummary;
import com.hisaablite.dto.ProductBatchVisibility;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.PurchaseBatch;
import com.hisaablite.entity.Shop;
import com.hisaablite.repository.PurchaseBatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchInventoryVisibilityService {

    private final PurchaseBatchRepository purchaseBatchRepository;
    private final ExpiryAlertService expiryAlertService;

    public Map<Long, ProductBatchVisibility> summarizeProducts(Shop shop, List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDate today = LocalDate.now();
        LocalDate nearExpiryCutoff = today.plusDays(30);

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();

        Map<Long, List<PurchaseBatch>> batchesByProduct = purchaseBatchRepository
                .findByShopAndActiveTrueAndProductIdIn(shop, productIds)
                .stream()
                .collect(Collectors.groupingBy(batch -> batch.getProduct().getId()));

        Map<Long, ProductBatchVisibility> summaries = new LinkedHashMap<>();
        for (Product product : products) {
            List<PurchaseBatch> batches = batchesByProduct.getOrDefault(product.getId(), Collections.emptyList());

            int activeBatchCount = 0;
            int liveBatchStock = 0;
            int sellableBatchStock = 0;
            int nearExpiryBatchCount = 0;
            int expiredBatchCount = 0;
            LocalDate nextSellableExpiryDate = null;

            for (PurchaseBatch batch : batches) {
                int availableQuantity = batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : 0;
                if (availableQuantity <= 0 || !batch.isActive()) {
                    continue;
                }

                activeBatchCount++;
                liveBatchStock += availableQuantity;

                LocalDate expiryDate = batch.getExpiryDate();
                boolean isExpired = expiryDate != null && expiryDate.isBefore(today);
                boolean isSellable = expiryDate == null || !expiryDate.isBefore(today);

                if (isExpired) {
                    expiredBatchCount++;
                }

                if (expiryDate != null && !expiryDate.isBefore(today) && !expiryDate.isAfter(nearExpiryCutoff)) {
                    nearExpiryBatchCount++;
                }

                if (isSellable) {
                    sellableBatchStock += availableQuantity;
                    if (expiryDate != null && (nextSellableExpiryDate == null || expiryDate.isBefore(nextSellableExpiryDate))) {
                        nextSellableExpiryDate = expiryDate;
                    }
                }
            }

            int manualLegacyStock = Math.max(0, (product.getStockQuantity() != null ? product.getStockQuantity() : 0) - liveBatchStock);
            int sellableStock = manualLegacyStock + sellableBatchStock;

            summaries.put(product.getId(), ProductBatchVisibility.builder()
                    .productId(product.getId())
                    .batchManaged(activeBatchCount > 0)
                    .activeBatchCount(activeBatchCount)
                    .liveBatchStock(liveBatchStock)
                    .sellableStock(sellableStock)
                    .nearExpiryBatchCount(nearExpiryBatchCount)
                    .expiredBatchCount(expiredBatchCount)
                    .nextSellableExpiryDate(nextSellableExpiryDate)
                    .lowStock(sellableStock <= (product.getMinStock() != null ? product.getMinStock() : 0))
                    .build());
        }

        return summaries;
    }

    public BatchDashboardSummary buildDashboardSummary(Shop shop, int expiringLimit) {
        return expiryAlertService.buildDashboardSummary(shop, expiringLimit);
    }
}
