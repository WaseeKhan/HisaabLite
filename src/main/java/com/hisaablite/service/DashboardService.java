package com.hisaablite.service;

import com.hisaablite.entity.*;
import com.hisaablite.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    // Get Top Selling Products
    public List<Map<String, Object>> getTopSellingProducts(Shop shop, int limit) {
        List<Map<String, Object>> topProducts = new ArrayList<>();
        
        try {
            List<Object[]> results = saleItemRepository.findTopSellingProductsByShop(
                    shop,
                    PageRequest.of(0, limit));
            
            if (results != null && !results.isEmpty()) {
                for (Object[] row : results) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("id", row[0] != null ? row[0] : 0);
                    product.put("name", row[1] != null ? row[1].toString() : "Unknown Product");
                    product.put("quantity", row[2] != null ? row[2] : 0);
                    product.put("revenue", row[3] != null ? row[3] : BigDecimal.ZERO);
                    topProducts.add(product);
                }
                log.info("Found {} top products", topProducts.size());
            }
        } catch (Exception e) {
            log.error("Error fetching top products: {}", e.getMessage());
        }
        
        return topProducts;
    }

    // Get Top Customers
    public List<Map<String, Object>> getTopCustomers(Shop shop, int limit) {
        List<Map<String, Object>> topCustomers = new ArrayList<>();
        
        try {
            List<Object[]> results = saleRepository.findTopCustomersByShop(
                    shop,
                    PageRequest.of(0, limit));
            
            if (results != null && !results.isEmpty()) {
                for (Object[] row : results) {
                    Map<String, Object> customer = new HashMap<>();
                    String customerName = row[0] != null ? row[0].toString() : "Walk-in Customer";
                    if (customerName.trim().isEmpty()) {
                        customerName = "Walk-in Customer";
                    }
                    customer.put("name", customerName);
                    customer.put("orders", row[1] != null ? row[1] : 0);
                    customer.put("total", row[2] != null ? row[2] : BigDecimal.ZERO);
                    topCustomers.add(customer);
                }
                log.info("Found {} top customers", topCustomers.size());
            }
        } catch (Exception e) {
            log.error("Error fetching top customers: {}", e.getMessage());
        }
        
        return topCustomers;
    }

    // Get Recent Activities - FIXED: Use SaleStatus.COMPLETED instead of String
    public List<Map<String, Object>> getRecentActivities(Shop shop, int limit) {
        List<Map<String, Object>> activities = new ArrayList<>();
        
        try {
            // Use Enum instead of String
            List<Sale> recentSales = saleRepository.findTop10ByShopAndStatusOrderBySaleDateDesc(shop, SaleStatus.COMPLETED);
            
            log.info("Recent sales found: {}", recentSales != null ? recentSales.size() : 0);
            
            if (recentSales != null && !recentSales.isEmpty()) {
                for (Sale sale : recentSales) {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("icon", "fas fa-receipt");
                    activity.put("text", "Sale completed - Invoice #INV-" + sale.getId());
                    activity.put("time", getTimeAgo(sale.getSaleDate()));
                    activity.put("amount", "₹" + (sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO));
                    activity.put("urgent", false);
                    activities.add(activity);
                    log.info("Added activity: Invoice #INV-{}", sale.getId());
                }
            } else {
                log.warn("No completed sales found in database for shop: {}", shop.getId());
            }
            
            // Low stock alerts
            List<Product> lowStockProducts = productRepository.findLowStockProducts(shop);
            if (lowStockProducts != null && !lowStockProducts.isEmpty()) {
                for (Product product : lowStockProducts) {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("icon", "fas fa-truck");
                    activity.put("text", "Low stock alert - " + (product.getName() != null ? product.getName() : "Unknown"));
                    activity.put("time", "Now");
                    activity.put("amount", (product.getStockQuantity() != null ? product.getStockQuantity() : 0) + " left");
                    activity.put("urgent", true);
                    activities.add(activity);
                }
                log.info("Added {} low stock alerts", lowStockProducts.size());
            }
            
            // Limit activities
            activities = activities.stream().limit(limit).collect(Collectors.toList());
            log.info("Total activities returned: {}", activities.size());
            
        } catch (Exception e) {
            log.error("Error fetching recent activities: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
        
        return activities;
    }

    // Helper method to get time ago
    private String getTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "Unknown";
        
        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(dateTime, now).toMinutes();
        
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minutes ago";
        if (minutes < 1440) return (minutes / 60) + " hours ago";
        return (minutes / 1440) + " days ago";
    }
}
