package com.hisaablite.service;

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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 import java.time.format.DateTimeFormatter;

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

        // 1 Create Sale
        Sale sale = new Sale();
        sale.setSaleDate(LocalDateTime.now());
        sale.setShop(shop);
        sale.setCreatedBy(createdBy);
        sale.setTotalAmount(BigDecimal.ZERO);
        sale.setStatus(SaleStatus.COMPLETED);

        Sale savedSale = saleRepository.save(sale);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // 2 Process each cart item
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

//cancel sale logic here

    @Transactional
    public void cancelSale(Long saleId) {

    Sale sale = saleRepository.findById(saleId)
            .orElseThrow(() -> new RuntimeException("Sale not found"));

    // Already cancelled check
    if (sale.getStatus() == SaleStatus.CANCELLED) {
        throw new RuntimeException("Sale already cancelled");
    }

    // Restore stock
    for (SaleItem item : sale.getItems()) {

        Product product = item.getProduct();

        product.setStockQuantity(product.getStockQuantity() + item.getQuantity());

        productRepository.save(product);
    }

    // Update sale status
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

    // Convert DB result to Map<Date, Revenue>
    Map<LocalDate, Double> revenueMap = new HashMap<>();

    for (Object[] row : results) {

    LocalDate date;

    // Safe date conversion
    if (row[0] instanceof java.sql.Date sqlDate) {
        date = sqlDate.toLocalDate();
    } else {
        date = (LocalDate) row[0];
    }

    //  SAFE BigDecimal → Double conversion
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
    labels.add(date.format(formatter));   // 23 Feb
    revenues.add(revenueMap.getOrDefault(date, 0.0));
}

    Map<String, Object> response = new HashMap<>();
    response.put("labels", labels);
    response.put("revenues", revenues);

    return response;
}
}