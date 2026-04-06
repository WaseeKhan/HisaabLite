package com.hisaablite.service;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import com.hisaablite.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> getProductsByShop(Shop shop) {
        return productRepository.findByShop(shop);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public List<Product> searchProducts(String keyword, Shop shop) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return productRepository.findByShopAndActiveTrue(shop);
        }
        return productRepository.searchProducts(shop, keyword.trim());
    }
    
    // ADD THIS NEW METHOD FOR PAGINATED SEARCH
    public Page<Product> searchProductsWithPagination(String keyword, Shop shop, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return productRepository.findByShopAndActiveTrue(shop, pageable);
        }
        return productRepository.searchProductsWithPagination(shop, keyword.trim(), pageable);
    }
    
    public Optional<Product> getProductByIdAndShop(Long id, Shop shop) {
        return productRepository.findByIdAndShop(id, shop);
    }
}
