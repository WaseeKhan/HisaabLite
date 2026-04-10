package com.expygen.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.expygen.dto.SupplierForm;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;
import com.expygen.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional
    public Supplier findOrCreateByName(Shop shop, String supplierName) {
        String normalizedName = normalizeRequiredName(supplierName);
        return supplierRepository.findByShopAndNameIgnoreCase(shop, normalizedName)
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .name(normalizedName)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .shop(shop)
                        .active(true)
                        .build()));
    }

    @Transactional
    public Supplier saveSupplier(Shop shop, SupplierForm form) {
        if (shop == null || shop.getId() == null) {
            throw new RuntimeException("Shop is required");
        }
        if (form == null) {
            throw new RuntimeException("Supplier form is missing");
        }

        String name = normalizeRequiredName(form.getName());

        Supplier supplier = form.getId() != null
                ? supplierRepository.findByIdAndShop(form.getId(), shop)
                        .orElseThrow(() -> new RuntimeException("Supplier not found"))
                : supplierRepository.findByShopAndNameIgnoreCase(shop, name).orElseGet(() -> Supplier.builder()
                        .shop(shop)
                        .createdAt(LocalDateTime.now())
                        .active(true)
                        .build());

        supplier.setName(name);
        supplier.setContactPerson(normalize(form.getContactPerson()));
        supplier.setPhone(normalize(form.getPhone()));
        supplier.setGstNumber(normalize(form.getGstNumber()));
        supplier.setAddress(normalize(form.getAddress()));
        supplier.setNotes(normalize(form.getNotes()));
        supplier.setUpdatedAt(LocalDateTime.now());
        if (supplier.getCreatedAt() == null) {
            supplier.setCreatedAt(LocalDateTime.now());
        }

        return supplierRepository.save(supplier);
    }

    public SupplierForm newSupplierForm() {
        return new SupplierForm();
    }

    public SupplierForm toForm(Supplier supplier) {
        SupplierForm form = new SupplierForm();
        form.setId(supplier.getId());
        form.setName(supplier.getName());
        form.setContactPerson(supplier.getContactPerson());
        form.setPhone(supplier.getPhone());
        form.setGstNumber(supplier.getGstNumber());
        form.setAddress(supplier.getAddress());
        form.setNotes(supplier.getNotes());
        return form;
    }

    public List<Supplier> listActiveSuppliers(Shop shop) {
        return supplierRepository.findByShopAndActiveTrueOrderByNameAsc(shop);
    }

    public List<Supplier> listTopSuppliers(Shop shop, int limit) {
        return supplierRepository.findByShopAndActiveTrueOrderByNameAsc(shop, PageRequest.of(0, limit)).getContent();
    }

    private String normalizeRequiredName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new RuntimeException("Supplier name is required");
        }
        return value.trim();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
