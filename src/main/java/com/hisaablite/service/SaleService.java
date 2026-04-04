package com.hisaablite.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hisaablite.admin.service.AuditService;
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
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    @Transactional
    public Sale completeSale(List<CartItem> cartItems, Shop shop, User createdBy) {
        return completeSale(cartItems, shop, createdBy, null, null, null, null, null, null, null);
    }

    @Transactional
    public Sale completeSale(
            List<CartItem> cartItems,
            Shop shop,
            User createdBy,
            String customerName,
            String customerPhone,
            String paymentMode,
            Double amountReceived,
            Double changeReturned,
            BigDecimal discountAmount,
            BigDecimal discountPercent) {
        try {
            validateSaleRequest(cartItems, shop, createdBy);
            List<CartItem> normalizedCartItems = normalizeCartItems(cartItems);
            List<LockedProductQuantity> lockedProducts = lockAndValidateProducts(normalizedCartItems, shop);

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
            List<SaleItem> savedItems = new ArrayList<>();

            for (LockedProductQuantity lockedProduct : lockedProducts) {
                Product product = lockedProduct.product();
                Integer quantity = lockedProduct.quantity();

                product.setStockQuantity(product.getStockQuantity() - quantity);
                productRepository.save(product);

                BigDecimal price = product.getPrice();
                Integer gstPercent = product.getGstPercent() != null ? product.getGstPercent() : 0;
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
                BigDecimal gstMultiplier = BigDecimal.valueOf(gstPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal gstAmount = subtotal.multiply(gstMultiplier);
                BigDecimal totalWithGst = subtotal.add(gstAmount);

                SaleItem saleItem = SaleItem.builder()
                        .sale(savedSale)
                        .product(product)
                        .quantity(quantity)
                        .priceAtSale(price)
                        .subtotal(subtotal)
                        .gstPercent(gstPercent)
                        .gstAmount(gstAmount)
                        .totalWithGst(totalWithGst)
                        .build();
                savedItems.add(saleItemRepository.save(saleItem));
                
                totalAmount = totalAmount.add(totalWithGst);
                totalGstAmount = totalGstAmount.add(gstAmount);
            }

            savedSale.setTotalAmount(totalAmount);
            savedSale.setTotalGstAmount(totalGstAmount);
            savedSale.setTaxableAmount(totalAmount.subtract(totalGstAmount));
            savedSale.setItems(savedItems);
            applySaleFinalization(savedSale, customerName, customerPhone, paymentMode, amountReceived, changeReturned,
                    discountAmount, discountPercent);

            saleRepository.save(savedSale);
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("items", savedItems.size());
            auditDetails.put("totalAmount", savedSale.getTotalAmount());
            auditDetails.put("discountAmount", savedSale.getDiscountAmount());
            auditDetails.put("paymentMode", savedSale.getPaymentMode());
            auditService.logAction(
                    createdBy.getUsername(),
                    createdBy.getRole().name(),
                    shop,
                    "SALE_COMPLETED",
                    "Sale",
                    savedSale.getId(),
                    "SUCCESS",
                    null,
                    auditDetails,
                    "Sale completed successfully");
            return savedSale;
        } catch (RuntimeException ex) {
            logSaleFailure(createdBy, "SALE_COMPLETED", ex.getMessage(), cartItems);
            throw ex;
        }
    }

    @Transactional
    public void cancelSale(Long saleId) {
        cancelSale(saleId, null);
    }

    @Transactional
    public void cancelSale(Long saleId, User actingUser) {
        Sale sale;
        try {
            sale = saleRepository.findByIdForUpdate(saleId)
                    .orElseThrow(() -> new RuntimeException("Sale not found"));

            if (sale.getStatus() == SaleStatus.CANCELLED) {
                throw new RuntimeException("Sale already cancelled");
            }

            for (SaleItem item : sale.getItems()) {
                Product product = loadVersionSafeProduct(item.getProduct().getId(), sale.getShop());
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }

            sale.setStatus(SaleStatus.CANCELLED);
            saleRepository.save(sale);
            User auditActor = actingUser != null ? actingUser : sale.getCreatedBy();
            auditService.logAction(
                    auditActor.getUsername(),
                    auditActor.getRole().name(),
                    sale.getShop(),
                    "SALE_CANCELLED",
                    "Sale",
                    sale.getId(),
                    "SUCCESS",
                    null,
                    Map.of("totalAmount", sale.getTotalAmount(), "items", sale.getItems().size()),
                    "Sale cancelled and stock restored");
        } catch (RuntimeException ex) {
            logSaleFailure(actingUser, "SALE_CANCELLED", ex.getMessage(), Map.of("saleId", saleId));
            throw ex;
        }
    }

    private void validateSaleRequest(List<CartItem> cartItems, Shop shop, User createdBy) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty!");
        }
        if (shop == null || shop.getId() == null) {
            throw new RuntimeException("Shop is required");
        }
        if (createdBy == null || createdBy.getId() == null) {
            throw new RuntimeException("Sale creator is required");
        }
        if (createdBy.getShop() == null || !shop.getId().equals(createdBy.getShop().getId())) {
            throw new RuntimeException("User does not belong to this shop");
        }
    }

    private List<CartItem> normalizeCartItems(List<CartItem> cartItems) {
        Map<Long, Integer> quantityByProductId = new HashMap<>();
        for (CartItem cartItem : cartItems) {
            validateCartItem(cartItem);
            quantityByProductId.merge(cartItem.getProductId(), cartItem.getQuantity(), Integer::sum);
        }

        return quantityByProductId.entrySet().stream()
                .map(entry -> CartItem.builder()
                        .productId(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(CartItem::getProductId))
                .toList();
    }

    private List<LockedProductQuantity> lockAndValidateProducts(List<CartItem> cartItems, Shop shop) {
        List<LockedProductQuantity> lockedProducts = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Product product = loadVersionSafeProduct(cartItem.getProductId(), shop);

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Not enough stock for product: " + product.getName());
            }

            lockedProducts.add(new LockedProductQuantity(product, cartItem.getQuantity()));
        }
        return lockedProducts;
    }

    private void validateCartItem(CartItem cartItem) {
        if (cartItem == null) {
            throw new RuntimeException("Cart contains an invalid item");
        }
        if (cartItem.getProductId() == null) {
            throw new RuntimeException("Cart item product is missing");
        }
        if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
            throw new RuntimeException("Quantity must be greater than zero");
        }
    }

    private Product loadVersionSafeProduct(Long productId, Shop shop) {
        Product product = productRepository.findByIdAndShopForUpdate(productId, shop)
                .orElseThrow(() -> new RuntimeException("Product not found for this shop"));

        if (product.getVersion() == null) {
            productRepository.initializeVersionIfMissing(productId);
            productRepository.flush();
            entityManager.detach(product);
            product = productRepository.findByIdAndShopForUpdate(productId, shop)
                    .orElseThrow(() -> new RuntimeException("Product not found for this shop"));
        }

        return product;
    }

    private void applySaleFinalization(
            Sale sale,
            String customerName,
            String customerPhone,
            String paymentMode,
            Double amountReceived,
            Double changeReturned,
            BigDecimal discountAmount,
            BigDecimal discountPercent) {
        BigDecimal normalizedDiscountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        BigDecimal normalizedDiscountPercent = discountPercent != null ? discountPercent : BigDecimal.ZERO;

        if (normalizedDiscountPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount % cannot be negative");
        }
        if (normalizedDiscountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RuntimeException("Discount % cannot exceed 100 %");
        }
        if (normalizedDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount cannot be negative");
        }

        BigDecimal originalTotal = sale.getTotalAmount();
        if (normalizedDiscountPercent.compareTo(BigDecimal.ZERO) > 0) {
            normalizedDiscountAmount = originalTotal
                    .multiply(normalizedDiscountPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        if (normalizedDiscountAmount.compareTo(originalTotal) > 0) {
            throw new RuntimeException("Discount cannot exceed total amount");
        }

        sale.setDiscountAmount(normalizedDiscountAmount);
        sale.setDiscountPercent(normalizedDiscountPercent);
        sale.setTotalAmount(originalTotal.subtract(normalizedDiscountAmount));
        sale.setCustomerName(customerName);
        sale.setCustomerPhone(customerPhone);
        sale.setPaymentMode(paymentMode);
        sale.setAmountReceived(amountReceived != null ? amountReceived : 0.0);
        sale.setChangeReturned(changeReturned != null ? changeReturned : 0.0);
    }

    private void logSaleFailure(User actor, String action, String message, Object details) {
        String username = actor != null ? actor.getUsername() : "SYSTEM";
        String role = actor != null ? actor.getRole().name() : "SYSTEM";
        auditService.logAction(
                username,
                role,
                actor != null ? actor.getShop() : null,
                action,
                "Sale",
                null,
                "FAILED",
                null,
                details,
                message);
    }

    private record LockedProductQuantity(Product product, Integer quantity) {
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
