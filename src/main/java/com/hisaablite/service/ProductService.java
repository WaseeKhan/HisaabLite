package com.hisaablite.service;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import com.hisaablite.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // Existing method
    public List<Product> getProductsByShop(Shop shop) {
        return productRepository.findByShop(shop);
    }

    // Existing method
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    //  NEW: AJAX Search Method
    public List<Product> searchProducts(String keyword, Shop shop) {
        return productRepository
                .findByShopAndNameIgnoreCaseContaining(shop, keyword);
    }

    public Optional<Product> getProductByIdAndShop(Long id, Shop shop) {
    return productRepository.findByIdAndShop(id, shop);
}
}