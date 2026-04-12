package com.expygen.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.expygen.entity.Product;

@Service
public class ProductBarcodeService {

    private static final String INTERNAL_PREFIX = "29";

    public String generateInternalBarcode(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Saved product is required to generate an internal barcode");
        }
        if (StringUtils.hasText(product.getBarcode())) {
            return product.getBarcode().trim();
        }

        String base = INTERNAL_PREFIX + String.format("%010d", product.getId());
        int checksum = calculateEan13CheckDigit(base);
        return base + checksum;
    }

    private int calculateEan13CheckDigit(String value) {
        if (value == null || value.length() != 12 || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("EAN-13 checksum requires exactly 12 digits");
        }

        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }
}
