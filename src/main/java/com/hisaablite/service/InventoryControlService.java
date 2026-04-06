package com.hisaablite.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.hisaablite.admin.service.AuditService;
import com.hisaablite.dto.PurchaseReturnForm;
import com.hisaablite.dto.PurchaseReturnLineForm;
import com.hisaablite.dto.StockAdjustmentForm;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.PurchaseBatch;
import com.hisaablite.entity.PurchaseReturn;
import com.hisaablite.entity.PurchaseReturnLine;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.StockAdjustment;
import com.hisaablite.entity.Supplier;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.PurchaseBatchRepository;
import com.hisaablite.repository.PurchaseReturnRepository;
import com.hisaablite.repository.StockAdjustmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryControlService {

    private final PurchaseBatchRepository purchaseBatchRepository;
    private final ProductRepository productRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final AuditService auditService;

    public PurchaseReturnForm newReturnForm() {
        PurchaseReturnForm form = new PurchaseReturnForm();
        form.setReturnDate(LocalDate.now());
        form.getItems().add(new PurchaseReturnLineForm());
        return form;
    }

    public StockAdjustmentForm newStockAdjustmentForm() {
        StockAdjustmentForm form = new StockAdjustmentForm();
        form.setAdjustmentDate(LocalDate.now());
        return form;
    }

    @Transactional
    public PurchaseReturn recordPurchaseReturn(PurchaseReturnForm form, Shop shop, User createdBy) {
        validateReturnRequest(form, shop, createdBy);

        PurchaseReturn purchaseReturn = PurchaseReturn.builder()
                .returnDate(form.getReturnDate() != null ? form.getReturnDate() : LocalDate.now())
                .createdAt(LocalDateTime.now())
                .notes(normalize(form.getNotes()))
                .shop(shop)
                .createdBy(createdBy)
                .build();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String supplierName = null;
        String referenceInvoiceNumber = null;
        Supplier supplier = null;

        for (PurchaseReturnLineForm line : form.getItems()) {
            if (isBlankReturnLine(line)) {
                continue;
            }

            PurchaseBatch batch = purchaseBatchRepository.findByIdForUpdate(line.getPurchaseBatchId())
                    .orElseThrow(() -> new RuntimeException("Batch not found for return"));
            ensureBatchBelongsToShop(batch, shop);

            Product product = productRepository.findByIdAndShopForUpdate(batch.getProduct().getId(), shop)
                    .orElseThrow(() -> new RuntimeException("Medicine not found for this batch"));

            int availableQuantity = batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : 0;
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new RuntimeException("Return quantity must be greater than zero");
            }
            if (line.getQuantity() > availableQuantity) {
                throw new RuntimeException("Cannot return more than available stock for batch " + batch.getBatchNumber());
            }

            String lineSupplier = batch.getPurchaseEntry() != null ? normalize(batch.getPurchaseEntry().getSupplierName()) : null;
            Supplier lineSupplierEntity = batch.getPurchaseEntry() != null ? batch.getPurchaseEntry().getSupplier() : null;
            if (supplierName == null) {
                supplierName = lineSupplier != null ? lineSupplier : "Supplier Return";
            } else if (lineSupplier != null && !supplierName.equalsIgnoreCase(lineSupplier)) {
                throw new RuntimeException("Return lines must belong to the same supplier");
            }
            if (supplier == null) {
                supplier = lineSupplierEntity;
            } else if (lineSupplierEntity != null && !supplier.getId().equals(lineSupplierEntity.getId())) {
                throw new RuntimeException("Return lines must belong to the same supplier");
            }

            String lineInvoice = batch.getPurchaseEntry() != null ? normalize(batch.getPurchaseEntry().getSupplierInvoiceNumber()) : null;
            if (referenceInvoiceNumber == null) {
                referenceInvoiceNumber = lineInvoice;
            } else if (lineInvoice == null || !referenceInvoiceNumber.equalsIgnoreCase(lineInvoice)) {
                referenceInvoiceNumber = "MULTI";
            }

            int updatedBatchQuantity = availableQuantity - line.getQuantity();
            int productStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            int updatedProductStock = productStock - line.getQuantity();
            if (updatedProductStock < 0) {
                throw new RuntimeException("Product stock cannot go negative while returning " + product.getName());
            }

            batch.setAvailableQuantity(updatedBatchQuantity);
            product.setStockQuantity(updatedProductStock);
            purchaseBatchRepository.save(batch);
            productRepository.save(product);

            BigDecimal lineAmount = batch.getPurchasePrice() != null
                    ? batch.getPurchasePrice().multiply(BigDecimal.valueOf(line.getQuantity()))
                    : BigDecimal.ZERO;

            purchaseReturn.getLines().add(PurchaseReturnLine.builder()
                    .purchaseReturn(purchaseReturn)
                    .purchaseBatch(batch)
                    .product(product)
                    .quantity(line.getQuantity())
                    .reason(normalize(line.getReason()))
                    .lineAmount(lineAmount)
                    .build());
            totalAmount = totalAmount.add(lineAmount);
        }

        if (purchaseReturn.getLines().isEmpty()) {
            throw new RuntimeException("Add at least one batch line to save a return");
        }

        purchaseReturn.setSupplier(supplier);
        purchaseReturn.setSupplierName(supplierName != null ? supplierName : "Supplier Return");
        purchaseReturn.setReferenceInvoiceNumber(referenceInvoiceNumber);
        purchaseReturn.setTotalAmount(totalAmount);
        PurchaseReturn finalReturn = purchaseReturnRepository.save(purchaseReturn);

        Map<String, Object> returnAudit = new HashMap<>();
        returnAudit.put("supplierName", finalReturn.getSupplierName());
        returnAudit.put("referenceInvoice", finalReturn.getReferenceInvoiceNumber() != null ? finalReturn.getReferenceInvoiceNumber() : "N/A");
        returnAudit.put("lineCount", purchaseReturn.getLines().size());
        returnAudit.put("returnedUnits", purchaseReturn.getLines().stream().mapToInt(PurchaseReturnLine::getQuantity).sum());
        returnAudit.put("totalAmount", finalReturn.getTotalAmount());

        auditService.logAction(
                createdBy.getUsername(),
                createdBy.getRole().name(),
                shop,
                "PURCHASE_RETURN_RECORDED",
                "PurchaseReturn",
                finalReturn.getId(),
                "SUCCESS",
                null,
                returnAudit,
                "Purchase return recorded against live batch stock");

        return finalReturn;
    }

    @Transactional
    public StockAdjustment recordStockAdjustment(StockAdjustmentForm form, Shop shop, User createdBy) {
        validateAdjustmentRequest(form, shop, createdBy);

        Product product;
        PurchaseBatch batch = null;
        Integer previousBatchQuantity = null;
        Integer newBatchQuantity = null;

        if (form.getPurchaseBatchId() != null) {
            batch = purchaseBatchRepository.findByIdForUpdate(form.getPurchaseBatchId())
                    .orElseThrow(() -> new RuntimeException("Batch not found for stock adjustment"));
            ensureBatchBelongsToShop(batch, shop);
            product = productRepository.findByIdAndShopForUpdate(batch.getProduct().getId(), shop)
                    .orElseThrow(() -> new RuntimeException("Medicine not found for this batch"));

            previousBatchQuantity = batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : 0;
            newBatchQuantity = previousBatchQuantity + form.getQuantityDelta();
            if (newBatchQuantity < 0) {
                throw new RuntimeException("Batch stock cannot go below zero");
            }
        } else {
            product = productRepository.findByIdAndShopForUpdate(form.getProductId(), shop)
                    .orElseThrow(() -> new RuntimeException("Medicine not found for stock adjustment"));

            if (form.getQuantityDelta() < 0) {
                int batchUnits = purchaseBatchRepository.sumAvailableQuantityByProduct(product);
                int manualUnits = Math.max(0, (product.getStockQuantity() != null ? product.getStockQuantity() : 0) - batchUnits);
                if (manualUnits + form.getQuantityDelta() < 0) {
                    throw new RuntimeException("Negative manual adjustment exceeds opening/manual stock for " + product.getName());
                }
            }
        }

        int previousProductStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int newProductStock = previousProductStock + form.getQuantityDelta();
        if (newProductStock < 0) {
            throw new RuntimeException("Product stock cannot go below zero");
        }

        if (batch != null) {
            batch.setAvailableQuantity(newBatchQuantity);
            purchaseBatchRepository.save(batch);
        }

        product.setStockQuantity(newProductStock);
        productRepository.save(product);

        StockAdjustment adjustment = stockAdjustmentRepository.save(StockAdjustment.builder()
                .adjustmentDate(form.getAdjustmentDate() != null ? form.getAdjustmentDate() : LocalDate.now())
                .createdAt(LocalDateTime.now())
                .quantityDelta(form.getQuantityDelta())
                .reason(normalize(form.getReason()))
                .notes(normalize(form.getNotes()))
                .previousProductStock(previousProductStock)
                .newProductStock(newProductStock)
                .previousBatchQuantity(previousBatchQuantity)
                .newBatchQuantity(newBatchQuantity)
                .shop(shop)
                .createdBy(createdBy)
                .product(product)
                .purchaseBatch(batch)
                .build());

        auditService.logAction(
                createdBy.getUsername(),
                createdBy.getRole().name(),
                shop,
                "STOCK_ADJUSTED",
                "StockAdjustment",
                adjustment.getId(),
                "SUCCESS",
                null,
                Map.of(
                        "productName", product.getName(),
                        "batchNumber", batch != null ? batch.getBatchNumber() : "MANUAL",
                        "quantityDelta", adjustment.getQuantityDelta(),
                        "reason", adjustment.getReason()),
                "Stock adjustment recorded");

        return adjustment;
    }

    private void validateReturnRequest(PurchaseReturnForm form, Shop shop, User createdBy) {
        if (form == null) {
            throw new RuntimeException("Purchase return form is missing");
        }
        validateActor(shop, createdBy);
        if (form.getItems() == null || form.getItems().stream().noneMatch(line -> !isBlankReturnLine(line))) {
            throw new RuntimeException("Add at least one batch line to save a return");
        }
    }

    private void validateAdjustmentRequest(StockAdjustmentForm form, Shop shop, User createdBy) {
        if (form == null) {
            throw new RuntimeException("Stock adjustment form is missing");
        }
        validateActor(shop, createdBy);
        if (form.getQuantityDelta() == null || form.getQuantityDelta() == 0) {
            throw new RuntimeException("Adjustment quantity must be greater or less than zero");
        }
        if (!StringUtils.hasText(form.getReason())) {
            throw new RuntimeException("Adjustment reason is required");
        }
        if (form.getPurchaseBatchId() == null && form.getProductId() == null) {
            throw new RuntimeException("Select a batch or medicine to adjust stock");
        }
    }

    private void validateActor(Shop shop, User createdBy) {
        if (shop == null || shop.getId() == null) {
            throw new RuntimeException("Shop is required");
        }
        if (createdBy == null || createdBy.getId() == null || createdBy.getShop() == null
                || !shop.getId().equals(createdBy.getShop().getId())) {
            throw new RuntimeException("Inventory action creator is invalid for this shop");
        }
    }

    private void ensureBatchBelongsToShop(PurchaseBatch batch, Shop shop) {
        if (batch.getShop() == null || !shop.getId().equals(batch.getShop().getId())) {
            throw new RuntimeException("Batch does not belong to this shop");
        }
    }

    private boolean isBlankReturnLine(PurchaseReturnLineForm line) {
        return line == null
                || (line.getPurchaseBatchId() == null
                        && line.getQuantity() == null
                        && !StringUtils.hasText(line.getReason()));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
