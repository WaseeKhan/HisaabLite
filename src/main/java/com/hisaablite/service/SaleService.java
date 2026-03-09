package com.hisaablite.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.hisaablite.dto.CartItem;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.SaleStatus;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Sale completeSale(List<CartItem> cartItems, Shop shop, User createdBy) {

        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty!");
        }

        // Create Sale
        Sale sale = new Sale();
        sale.setSaleDate(LocalDateTime.now());
        sale.setShop(shop);
        sale.setCreatedBy(createdBy);
        sale.setTotalAmount(BigDecimal.ZERO);
        sale.setTotalGstAmount(BigDecimal.ZERO);
        sale.setTaxableAmount(BigDecimal.ZERO);
        sale.setStatus(SaleStatus.COMPLETED);

        Sale savedSale = saleRepository.save(sale);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalGstAmount = BigDecimal.ZERO;

        // Process each cart item
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

            // Calculate GST
            BigDecimal price = product.getPrice();
            Integer gstPercent = product.getGstPercent() != null ? product.getGstPercent() : 0;
            
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            
            // GST calculation
            BigDecimal gstMultiplier = BigDecimal.valueOf(gstPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal gstAmount = subtotal.multiply(gstMultiplier);
            BigDecimal totalWithGst = subtotal.add(gstAmount);

            // Save SaleItem with GST
            SaleItem saleItem = SaleItem.builder()
                    .sale(savedSale)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .priceAtSale(price)
                    .subtotal(subtotal)
                    .gstPercent(gstPercent)
                    .gstAmount(gstAmount)
                    .totalWithGst(totalWithGst)
                    .build();
            saleItemRepository.save(saleItem);
            
            totalAmount = totalAmount.add(totalWithGst);
            totalGstAmount = totalGstAmount.add(gstAmount);
        }

        // Update totals
        savedSale.setTotalAmount(totalAmount);
        savedSale.setTotalGstAmount(totalGstAmount);
        savedSale.setTaxableAmount(totalAmount.subtract(totalGstAmount));
        
        saleRepository.save(savedSale);

        return savedSale;
    }

    @Transactional
    public void cancelSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new RuntimeException("Sale already cancelled");
        }

        // Restore stock
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        sale.setStatus(SaleStatus.CANCELLED);
        saleRepository.save(sale);
    }

    public List<String> getLast7DaysLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            labels.add(LocalDate.now().minusDays(i).toString());
        }
        return labels;
    }

    public Map<String, Object> getLast7DaysChartData(Shop shop) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        List<Object[]> results = saleRepository.getLast7DaysRevenue(shop, start);
        Map<LocalDate, Double> revenueMap = new HashMap<>();

        for (Object[] row : results) {
            LocalDate date;
            if (row[0] instanceof java.sql.Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else {
                date = (LocalDate) row[0];
            }
            Double total = 0.0;
            if (row[1] instanceof java.math.BigDecimal bigDecimal) {
                total = bigDecimal.doubleValue();
            } else if (row[1] instanceof Double d) {
                total = d;
            }
            revenueMap.put(date, total);
        }

        List<String> labels = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            labels.add(date.format(formatter));
            revenues.add(revenueMap.getOrDefault(date, 0.0));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("labels", labels);
        response.put("revenues", revenues);
        return response;
    }
}