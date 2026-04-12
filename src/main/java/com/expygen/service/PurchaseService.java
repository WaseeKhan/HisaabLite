package com.expygen.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.expygen.admin.service.AuditService;
import com.expygen.dto.PurchaseEntryForm;
import com.expygen.dto.PurchaseLineForm;
import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseEntryRepository purchaseEntryRepository;
    private final PurchaseBatchRepository purchaseBatchRepository;
    private final ProductRepository productRepository;
    private final SupplierService supplierService;
    private final AuditService auditService;

    @Transactional
    public PurchaseEntry recordPurchase(PurchaseEntryForm form, Shop shop, User createdBy) {
        validatePurchaseForm(form, shop, createdBy);
        Supplier supplier = supplierService.findOrCreateByName(shop, form.getSupplierName());

        PurchaseEntry entry = PurchaseEntry.builder()
                .purchaseDate(form.getPurchaseDate() != null ? form.getPurchaseDate() : LocalDate.now())
                .createdAt(LocalDateTime.now())
                .supplier(supplier)
                .supplierName(supplier.getName())
                .supplierInvoiceNumber(normalizeText(form.getSupplierInvoiceNumber()))
                .notes(normalizeText(form.getNotes()))
                .shop(shop)
                .createdBy(createdBy)
                .build();

        PurchaseEntry savedEntry = purchaseEntryRepository.save(entry);
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseBatch> savedBatches = new ArrayList<>();

        for (PurchaseLineForm line : form.getItems()) {
            if (isBlankLine(line)) {
                continue;
            }

            Product product = productRepository.findByIdAndShopForUpdate(line.getProductId(), shop)
                    .orElseThrow(() -> new RuntimeException("Medicine not found for this shop"));

            PurchaseBatch batch = PurchaseBatch.builder()
                    .purchaseEntry(savedEntry)
                    .shop(shop)
                    .product(product)
                    .batchNumber(line.getBatchNumber().trim())
                    .expiryDate(line.getExpiryDate())
                    .purchasePrice(line.getPurchasePrice())
                    .mrp(line.getMrp())
                    .salePrice(line.getSalePrice())
                    .receivedQuantity(line.getQuantity())
                    .availableQuantity(line.getQuantity())
                    .createdAt(LocalDateTime.now())
                    .build();
            savedBatches.add(purchaseBatchRepository.save(batch));

            product.setPurchasePrice(line.getPurchasePrice());
            if (line.getMrp() != null) {
                product.setMrp(line.getMrp());
            }
            if (line.getSalePrice() != null) {
                product.setPrice(line.getSalePrice());
            }
            product.setStockQuantity(product.getStockQuantity() + line.getQuantity());
            productRepository.save(product);

            totalAmount = totalAmount.add(line.getPurchasePrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }

        savedEntry.getBatches().clear();
        savedEntry.getBatches().addAll(savedBatches);
        savedEntry.setTotalAmount(totalAmount);
        PurchaseEntry finalEntry = purchaseEntryRepository.save(savedEntry);

        auditService.logAction(
                createdBy.getUsername(),
                createdBy.getRole().name(),
                shop,
                "PURCHASE_RECORDED",
                "PurchaseEntry",
                finalEntry.getId(),
                "SUCCESS",
                null,
                Map.of(
                        "supplierId", supplier.getId(),
                        "supplierName", finalEntry.getSupplierName(),
                        "invoiceNumber", finalEntry.getSupplierInvoiceNumber(),
                        "batchCount", savedBatches.size(),
                        "totalAmount", finalEntry.getTotalAmount()),
                "Purchase entry recorded with batch stock");

        return finalEntry;
    }

    public PurchaseEntryForm newEntryForm() {
        PurchaseEntryForm form = new PurchaseEntryForm();
        form.setPurchaseDate(LocalDate.now());
        form.getItems().add(new PurchaseLineForm());
        return form;
    }

    private void validatePurchaseForm(PurchaseEntryForm form, Shop shop, User createdBy) {
        if (form == null) {
            throw new RuntimeException("Purchase form is missing");
        }
        if (shop == null || shop.getId() == null) {
            throw new RuntimeException("Shop is required");
        }
        if (createdBy == null || createdBy.getId() == null || createdBy.getShop() == null
                || !shop.getId().equals(createdBy.getShop().getId())) {
            throw new RuntimeException("Purchase creator is invalid for this shop");
        }
        if (!StringUtils.hasText(form.getSupplierName())) {
            throw new RuntimeException("Supplier name is required");
        }
        if (form.getItems() == null || form.getItems().stream().noneMatch(line -> !isBlankLine(line))) {
            throw new RuntimeException("Add at least one medicine line to save a purchase");
        }

        for (PurchaseLineForm line : form.getItems()) {
            if (isBlankLine(line)) {
                continue;
            }
            if (line.getProductId() == null) {
                throw new RuntimeException("Select a medicine for each purchase line");
            }
            if (!StringUtils.hasText(line.getBatchNumber())) {
                throw new RuntimeException("Batch number is required for every medicine line");
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new RuntimeException("Purchase quantity must be greater than zero");
            }
            if (line.getPurchasePrice() == null || line.getPurchasePrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Purchase price must be greater than zero");
            }
            if (line.getSalePrice() != null && line.getSalePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Sale price cannot be negative");
            }
            if (line.getMrp() != null && line.getMrp().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("MRP cannot be negative");
            }
        }
    }

    private boolean isBlankLine(PurchaseLineForm line) {
        return line == null
                || (line.getProductId() == null
                        && !StringUtils.hasText(line.getBatchNumber())
                        && line.getQuantity() == null
                        && line.getPurchasePrice() == null
                        && line.getSalePrice() == null
                        && line.getMrp() == null
                        && line.getExpiryDate() == null);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
