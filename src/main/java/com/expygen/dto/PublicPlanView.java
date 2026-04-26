package com.expygen.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicPlanView {

    private final String planName;
    private final String description;
    private final Double price;
    private final Integer durationInDays;
    private final Integer maxUsers;
    private final Integer maxProducts;
    private final String totalPriceLabel;
    private final String totalPricePeriodLabel;
    private final String annualListPriceLabel;
    private final String annualDiscountLabel;
    private final String monthlyPriceLabel;
    private final String durationLabel;
    private final String usersLabel;
    private final String productsLabel;
    private final String actionLabel;
    private final String actionHref;
    private final boolean free;
    private final boolean popular;
    private final boolean enterprise;
}
