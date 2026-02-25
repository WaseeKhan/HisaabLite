package com.hisaablite.service;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import com.hisaablite.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
}