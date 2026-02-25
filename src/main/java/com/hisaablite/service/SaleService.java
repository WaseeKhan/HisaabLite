package com.hisaablite.service;

import com.hisaablite.dto.CartItem;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    //  Full transactional sale
    @Transactional
    public Sale completeSale(List<CartItem> cartItems, Shop shop, User createdBy) {

        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty!");
        }

        // 1️⃣ Create Sale
        Sale sale = new Sale();
        sale.setSaleDate(LocalDateTime.now());
        sale.setShop(shop);
        sale.setCreatedBy(createdBy);
        sale.setTotalAmount(BigDecimal.ZERO);

        Sale savedSale = saleRepository.save(sale);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // 2️⃣ Process each cart item
        for (CartItem cartItem : cartItems) {

            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Stock validation
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Not enough stock for product: " + product.getName());
            }

            // Deduct stock
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            // Save SaleItem
            SaleItem saleItem = SaleItem.builder()
                    .sale(savedSale)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .priceAtSale(product.getPrice())
                    .subtotal(subtotal)
                    .build();

            saleItemRepository.save(saleItem);

            totalAmount = totalAmount.add(subtotal);
        }

        // Update total amount
        savedSale.setTotalAmount(totalAmount);
        saleRepository.save(savedSale);

        return savedSale;
    }
}