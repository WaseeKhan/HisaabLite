package com.expygen.service;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.expygen.dto.PublicPlanView;
import com.expygen.entity.PlanType;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.repository.SubscriptionPlanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PublicPricingService {

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.##");

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public List<PublicPlanView> getActivePublicPlans() {
        return subscriptionPlanRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparingInt(this::planOrder))
                .map(this::toView)
                .toList();
    }

    private PublicPlanView toView(SubscriptionPlan plan) {
        String planName = normalize(plan.getPlanName());
        double annualPrice = plan.getEffectiveAnnualPrice() == null ? 0.0d : plan.getEffectiveAnnualPrice();
        double annualListPrice = plan.getAnnualListPrice() == null ? 0.0d : plan.getAnnualListPrice();
        double annualDiscount = plan.getEffectiveAnnualDiscountPercent() == null ? 0.0d : plan.getEffectiveAnnualDiscountPercent();
        boolean isFree = annualPrice <= 0;

        return PublicPlanView.builder()
                .planName(planName)
                .description(plan.getDescription())
                .price(annualPrice)
                .durationInDays(plan.getDurationInDays())
                .maxUsers(plan.getMaxUsers())
                .maxProducts(plan.getMaxProducts())
                .totalPriceLabel(isFree ? "₹0" : formatCurrency(annualPrice))
                .totalPricePeriodLabel(isFree ? "Start without upfront cost" : "/year")
                .annualListPriceLabel(formatCurrency(annualListPrice))
                .annualDiscountLabel(annualDiscount > 0 ? "Save " + PRICE_FORMAT.format(annualDiscount) + "%" : "")
                .monthlyPriceLabel(isFree ? "₹0" : formatMonthlyEquivalent(plan.getPrice(), plan.getDurationInDays()))
                .durationLabel(formatDuration(plan.getDurationInDays()))
                .usersLabel(formatLimit(plan.getMaxUsers()))
                .productsLabel(formatLimit(plan.getMaxProducts()))
                .actionLabel(resolveActionLabel(planName))
                .actionHref("/register?plan=" + planName)
                .free(isFree)
                .popular("PRO".equalsIgnoreCase(planName))
                .enterprise(false)
                .build();
    }

    private int planOrder(SubscriptionPlan plan) {
        try {
            return PlanType.valueOf(normalize(plan.getPlanName())).ordinal();
        } catch (IllegalArgumentException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private String resolveActionLabel(String planName) {
        return switch (planName.toUpperCase(Locale.ROOT)) {
            case "FREE" -> "Start Free";
            case "BASIC" -> "Choose Basic";
            case "PRO" -> "Choose Pro";
            default -> "Choose Plan";
        };
    }

    private String formatLimit(Integer value) {
        if (value == null) {
            return "-";
        }
        if (value == -1) {
            return "Unlimited";
        }
        return String.valueOf(value);
    }

    private String formatDuration(Integer durationInDays) {
        if (durationInDays == null || durationInDays <= 0) {
            return "No expiry";
        }
        if (durationInDays == 30) {
            return "30 days";
        }
        if (durationInDays == 90) {
            return "3 months";
        }
        if (durationInDays == 180) {
            return "6 months";
        }
        if (durationInDays == 365) {
            return "1 year";
        }
        return durationInDays + " days";
    }

    private String formatTotalPeriod(Integer durationInDays) {
        if (durationInDays == null || durationInDays <= 0) {
            return "One-time activation";
        }
        if (durationInDays == 365) {
            return "/year";
        }
        if (durationInDays == 30) {
            return "/month";
        }
        return "/" + formatDuration(durationInDays).toLowerCase(Locale.ROOT);
    }

    private String formatMonthlyEquivalent(Double totalPrice, Integer durationInDays) {
        if (totalPrice == null || totalPrice <= 0) {
            return "₹0";
        }
        if (durationInDays == null || durationInDays <= 0) {
            return formatCurrency(totalPrice);
        }

        double months = durationInDays / 30.0d;
        if (months <= 0) {
            return formatCurrency(totalPrice);
        }

        return formatCurrency(totalPrice / months);
    }

    private String formatCurrency(Double value) {
        if (value == null) {
            return "₹0";
        }
        return "₹" + PRICE_FORMAT.format(value);
    }

    private String normalize(String planName) {
        return planName == null ? "" : planName.trim().toUpperCase(Locale.ROOT);
    }
}
