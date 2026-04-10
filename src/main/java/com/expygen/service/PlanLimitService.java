package com.expygen.service;

import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.entity.PlanType;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.UserRepository;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanLimitService {

    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;

    /**
     * Get the current plan for a shop dynamically from database
     */
    public SubscriptionPlan getCurrentPlan(Shop shop) {
        if (shop.getPlanType() == null) {
            // Default to FREE plan if no plan set
            return planRepository.findByPlanName("FREE")
                    .orElseThrow(() -> new RuntimeException("FREE plan not configured in database"));
        }

        // Fetch the plan dynamically from database using the plan name
        return planRepository.findByPlanName(shop.getPlanType().name())
                .orElseThrow(() -> new RuntimeException(
                        "Plan " + shop.getPlanType()
                                + " not found in database. Please check subscription_plans table."));
    }

    /**
     * Check if shop can add more users based on current plan limits
     */
    public boolean canAddUser(Shop shop) {
        SubscriptionPlan plan = getCurrentPlan(shop);

        // -1 means unlimited
        if (plan.getMaxUsers() == -1) {
            log.debug("Shop {}: Unlimited users allowed", shop.getId());
            return true;
        }

        long currentUsers = userRepository.countByShop(shop);
        boolean canAdd = currentUsers < plan.getMaxUsers();

        log.debug("Shop {}: current users={}, max={} (from plan: {}), canAdd={}",
                shop.getId(), currentUsers, plan.getMaxUsers(), plan.getPlanName(), canAdd);

        return canAdd;
    }

    /**
     * Check if shop can add more products based on current plan limits
     */
    public boolean canAddProduct(Shop shop) {
        SubscriptionPlan plan = getCurrentPlan(shop);

        // -1 means unlimited
        if (plan.getMaxProducts() == -1) {
            log.debug("Shop {}: Unlimited products allowed", shop.getId());
            return true;
        }

        // FIXED: Count only ACTIVE products
        long currentProducts = productRepository.countByShopAndActiveTrue(shop);
        boolean canAdd = currentProducts < plan.getMaxProducts();

        log.debug("Shop {}: current active products={}, max={} (from plan: {}), canAdd={}",
                shop.getId(), currentProducts, plan.getMaxProducts(), plan.getPlanName(), canAdd);

        return canAdd;
    }

    /**
     * Get user limit for shop from database
     */
    public int getUserLimit(Shop shop) {
        SubscriptionPlan plan = getCurrentPlan(shop);
        return plan.getMaxUsers();
    }

    /**
     * Get product limit for shop from database
     */
    public int getProductLimit(Shop shop) {
        SubscriptionPlan plan = getCurrentPlan(shop);
        return plan.getMaxProducts();
    }

    /**
     * Get plan features/description
     */
    public String getPlanFeatures(Shop shop) {
        SubscriptionPlan plan = getCurrentPlan(shop);
        return plan.getFeatures() != null ? plan.getFeatures() : plan.getDescription();
    }

    /**
     * Check if subscription is active (not expired)
     */
    public boolean isSubscriptionActive(Shop shop) {
        // FREE plan never expires (unless admin sets duration)
        if (shop.getPlanType() == PlanType.FREE) {
            SubscriptionPlan plan = getCurrentPlan(shop);
            if (plan.getDurationInDays() == null || plan.getDurationInDays() <= 0) {
                return true; // FREE plan with no expiry
            }
        }

        // Check expiry date
        if (shop.getSubscriptionEndDate() == null) {
            return true; // No expiry set
        }

        return LocalDateTime.now().isBefore(shop.getSubscriptionEndDate());
    }

    /**
     * Get days remaining until subscription expires
     */
    public Long getDaysRemaining(Shop shop) {
        if (shop.getSubscriptionEndDate() == null) {
            return null; // No expiry
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(shop.getSubscriptionEndDate())) {
            return 0L; // Already expired
        }

        return Duration.between(now, shop.getSubscriptionEndDate()).toDays();
    }

    /**
     * Get detailed usage statistics for dashboard
     */
    public Map<String, Object> getUsageStats(Shop shop) {
        Map<String, Object> stats = new HashMap<>();

        try {
            SubscriptionPlan plan = getCurrentPlan(shop);

            // Current usage counts - FIXED: Use active counts
            long currentUsers = userRepository.countByShop(shop);
            long currentProducts = productRepository.countByShopAndActiveTrue(shop); // FIXED
            long currentSales = saleRepository.countByShop(shop);

            // Plan limits from database
            int maxUsers = plan.getMaxUsers();
            int maxProducts = plan.getMaxProducts();

            stats.put("planId", plan.getId());
            stats.put("planName", plan.getPlanName());
            stats.put("planDescription", plan.getDescription());
            stats.put("planFeatures", plan.getFeatures());
            stats.put("planPrice", plan.getPrice());
            stats.put("planDuration", plan.getDurationInDays());

            // User stats
            stats.put("maxUsers", maxUsers == -1 ? "Unlimited" : maxUsers);
            stats.put("currentUsers", currentUsers);
            stats.put("usersPercentage", calculatePercentage(currentUsers, maxUsers));
            stats.put("usersRemaining", maxUsers == -1 ? -1 : Math.max(0, maxUsers - currentUsers));

            // Product stats - FIXED: Use active products count
            stats.put("maxProducts", maxProducts == -1 ? "Unlimited" : maxProducts);
            stats.put("currentProducts", currentProducts);
            stats.put("productsPercentage", calculatePercentage(currentProducts, maxProducts));
            stats.put("productsRemaining", maxProducts == -1 ? -1 : Math.max(0, maxProducts - currentProducts));

            // Sales stats (optional)
            stats.put("totalSales", currentSales);

            // Subscription status
            stats.put("subscriptionActive", isSubscriptionActive(shop));
            stats.put("daysRemaining", getDaysRemaining(shop));
            stats.put("expiryDate", shop.getSubscriptionEndDate());

            log.debug("Usage stats for shop {}: {} users/{}, {} active products/{}",
                    shop.getId(), currentUsers, maxUsers, currentProducts, maxProducts);

        } catch (Exception e) {
            log.error("Error getting usage stats for shop {}: {}", shop.getId(), e.getMessage());
            stats.put("error", "Could not load plan details");
        }

        return stats;
    }

    /**
     * Validate if shop can perform an action based on limits
     */
    public void validateAction(Shop shop, String actionType) throws RuntimeException {
        SubscriptionPlan plan = getCurrentPlan(shop);

        // Check subscription expiry first
        if (!isSubscriptionActive(shop)) {
            throw new RuntimeException(
                    "Your " + plan.getPlanName() + " subscription has expired. Please renew to continue.");
        }

        switch (actionType) {
            case "ADD_USER":
                if (!canAddUser(shop)) {
                    throw new RuntimeException(
                            String.format("User limit reached! Your %s plan allows maximum %d users. " +
                                    "Current: %d",
                                    plan.getPlanName(),
                                    plan.getMaxUsers(),
                                    userRepository.countByShop(shop)));
                }
                break;

            case "ADD_PRODUCT":
                if (!canAddProduct(shop)) {
                    throw new RuntimeException(
                            String.format("Product limit reached! Your %s plan allows maximum %d products. " +
                                    "Current: %d",
                                    plan.getPlanName(),
                                    plan.getMaxProducts(),
                                    productRepository.countByShop(shop)));
                }
                break;

            default:
                log.warn("Unknown action type: {}", actionType);
        }
    }

    /**
     * Get all available plans for upgrade page
     */
    public List<SubscriptionPlan> getAvailablePlans(Shop shop) {
        List<SubscriptionPlan> allPlans = planRepository.findByActiveTrue();

        // You might want to filter out current plan or sort by price
        return allPlans.stream()
                .filter(p -> !p.getPlanName().equals(shop.getPlanType().name()))
                .sorted((a, b) -> a.getPrice().compareTo(b.getPrice()))
                .collect(Collectors.toList());
    }

    /**
     * Calculate upgrade price if changing plans
     */
    public double calculateUpgradePrice(Shop shop, String newPlanName) {
        SubscriptionPlan currentPlan = getCurrentPlan(shop);
        SubscriptionPlan newPlan = planRepository.findByPlanName(newPlanName)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        // Prorated calculation based on remaining days
        Long remainingDays = getDaysRemaining(shop);
        if (remainingDays != null && remainingDays > 0) {
            double dailyRate = currentPlan.getPrice() / 30; // Assuming monthly
            double credit = dailyRate * remainingDays;
            return Math.max(0, newPlan.getPrice() - credit);
        }

        return newPlan.getPrice();
    }

    private int calculatePercentage(long current, int max) {
        if (max == -1)
            return 0; // Unlimited
        if (max <= 0)
            return 100;
        return (int) ((current * 100) / max);
    }

    public long getCurrentProductCount(Shop shop) {
        return productRepository.countByShop(shop);
    }
}