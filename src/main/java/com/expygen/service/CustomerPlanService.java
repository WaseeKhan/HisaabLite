package com.expygen.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.expygen.admin.repository.AdminSubscriptionRepository;
import com.expygen.dto.RegisterPlanDTO;
import com.expygen.entity.SubscriptionPlan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerPlanService {

    private final AdminSubscriptionRepository subscriptionRepository;

    public List<RegisterPlanDTO> getPlansForRegistration() {
        List<SubscriptionPlan> plans = subscriptionRepository.findByActiveTrue();
        
        return plans.stream()
            .map(this::convertToRegisterDTO)
            .collect(Collectors.toList());
    }

    private RegisterPlanDTO convertToRegisterDTO(SubscriptionPlan plan) {
        RegisterPlanDTO dto = new RegisterPlanDTO(
            plan.getPlanName(),
            plan.getPrice(),
            plan.getMaxUsers(),
            plan.getMaxProducts(),
            plan.getDescription(),
            plan.getFeatures()
        );
        
        
        if (plan.getPlanName().equals("PRO")) {
            dto.setBadge("Popular");
        } else if (plan.getPlanName().equals("BASIC")) {
            dto.setBadge("Best Value");
        }
        
        return dto;
    }
}