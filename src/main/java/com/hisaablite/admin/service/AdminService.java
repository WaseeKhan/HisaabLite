package com.hisaablite.admin.service;

import com.hisaablite.admin.dto.AdminDashboardDTO;
import com.hisaablite.admin.dto.PopularPlanDTO;
import com.hisaablite.admin.repository.AdminShopRepository;
import com.hisaablite.admin.repository.AdminUserRepository;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AdminUserRepository adminUserRepo;
    private final AdminShopRepository adminShopRepo;

    public AdminDashboardDTO getDashboardStats() {
        AdminDashboardDTO dto = new AdminDashboardDTO();

        try {
            // ===== BASIC COUNTS =====
            dto.setTotalUsers(adminUserRepo.count());
            dto.setTotalShops(adminShopRepo.count());
            dto.setActiveUsers(adminUserRepo.countByActiveTrue());
            dto.setPendingApprovals(adminUserRepo.countByApprovedFalse());
            dto.setActiveShops(adminShopRepo.countByActive(true));
            dto.setInactiveShops(adminShopRepo.countByActive(false));

            // ===== PLAN STATISTICS =====
            List<PopularPlanDTO> planStats = getPlanStatistics();
            dto.setPopularPlans(planStats);

            // Extract plan labels and data for chart
            List<String> planLabels = new ArrayList<>();
            List<Long> planData = new ArrayList<>();
            for (PopularPlanDTO plan : planStats) {
                planLabels.add(plan.getPlanName());
                planData.add(plan.getShopCount());
            }
            dto.setPlanLabels(planLabels);
            dto.setPlanData(planData);

            // ===== RECENT SHOPS =====
            List<Map<String, Object>> recentShops = new ArrayList<>();
            List<Shop> shops = adminShopRepo.findTop5ByOrderByCreatedAtDesc(); 
            for (Shop shop : shops) {
                Map<String, Object> shopMap = new HashMap<>();
                shopMap.put("id", shop.getId());
                shopMap.put("name", shop.getName());
                shopMap.put("planType", shop.getPlanType() != null ? shop.getPlanType().name() : "FREE");
                shopMap.put("active", shop.isActive());
                shopMap.put("createdAt", shop.getCreatedAt());
                shopMap.put("owner", getShopOwnerName(shop));
                recentShops.add(shopMap);
            }
            dto.setRecentShops(recentShops);

            // ===== RECENT USERS =====
            List<Map<String, Object>> recentUsers = new ArrayList<>();
            List<User> users = adminUserRepo.findTop5ByOrderByCreatedAtDesc(); 
            for (User user : users) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("name", user.getName());
                userMap.put("username", user.getUsername());
                userMap.put("role", user.getRole() != null ? user.getRole().name() : "USER");
                userMap.put("active", user.isActive());
                userMap.put("approved", user.isApproved());
                userMap.put("shop", user.getShop() != null ? user.getShop().getName() : "No Shop");
                recentUsers.add(userMap);
            }
            dto.setRecentUsers(recentUsers);

            // ===== CHART DATA =====
            dto.setRevenueLabels(getLast7DaysLabels());
            dto.setRevenueData(getDummyRevenueData());
            dto.setUsersData(getUserGrowthData());
            dto.setPeriod("Last 7 days");

            log.info("Dashboard stats loaded successfully - Shops: {}, Users: {}",
                    dto.getTotalShops(), dto.getTotalUsers());

        } catch (Exception e) {
            log.error("Error loading dashboard: {}", e.getMessage(), e);
            setDefaultValues(dto);
        }

        return dto;
    }

    private String getShopOwnerName(Shop shop) {
        if (shop.getUsers() != null) {
            return shop.getUsers().stream()
                    .filter(u -> u.getRole() != null && u.getRole() == Role.OWNER) 
                    .findFirst()
                    .map(User::getName)
                    .orElse("N/A");
        }
        return "N/A";
    }

    private List<PopularPlanDTO> getPlanStatistics() {
        List<PopularPlanDTO> planStats = new ArrayList<>();

        try {
            // Initialize counters
            Map<String, Long> counts = new HashMap<>();
            counts.put("FREE", 0L);
            counts.put("BASIC", 0L);
            counts.put("PREMIUM", 0L);
            counts.put("ENTERPRISE", 0L);

            // Get all shops
            List<Shop> allShops = adminShopRepo.findAll();

            // Count by plan type
            for (Shop shop : allShops) {
                PlanType plan = shop.getPlanType();
                if (plan != null) {
                    counts.merge(plan.name(), 1L, Long::sum);
                } else {
                    counts.merge("FREE", 1L, Long::sum);
                }
            }

            // Convert to DTOs
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                planStats.add(new PopularPlanDTO(entry.getKey(), entry.getValue()));
            }

            // Sort by predefined order
            List<String> order = Arrays.asList("FREE", "BASIC", "PREMIUM", "ENTERPRISE");
            planStats.sort(Comparator.comparingInt(a -> order.indexOf(a.getPlanName())));

        } catch (Exception e) {
            log.error("Error getting plan stats: {}", e.getMessage());
            // Default values
            planStats.add(new PopularPlanDTO("FREE", 0L));
            planStats.add(new PopularPlanDTO("BASIC", 0L));
            planStats.add(new PopularPlanDTO("PREMIUM", 0L));
            planStats.add(new PopularPlanDTO("ENTERPRISE", 0L));
        }

        return planStats;
    }

    private List<String> getLast7DaysLabels() {
        List<String> labels = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            labels.add(today.minusDays(i).format(formatter));
        }
        return labels;
    }

    private List<Double> getDummyRevenueData() {
        List<Double> data = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < 7; i++) {
            data.add(5000 + rand.nextDouble() * 15000);
        }
        return data;
    }

    private List<Long> getUserGrowthData() {
        List<Long> userData = new ArrayList<>();
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(7);

            List<Object[]> results = adminUserRepo.getUserStatsByDate(startDate, endDate);

            // Create a map of date -> count
            Map<LocalDate, Long> dateCountMap = new HashMap<>();
            for (Object[] result : results) {
                if (result[0] != null && result[1] != null) {
                    LocalDate date = null;
                    if (result[0] instanceof java.sql.Date) {
                        date = ((java.sql.Date) result[0]).toLocalDate();
                    } else if (result[0] instanceof LocalDate) {
                        date = (LocalDate) result[0];
                    }

                    if (date != null) {
                        Long count = ((Number) result[1]).longValue();
                        dateCountMap.put(date, count);
                    }
                }
            }

            // Fill data for each day
            LocalDate today = LocalDate.now();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                userData.add(dateCountMap.getOrDefault(date, 0L));
            }

        } catch (Exception e) {
            log.error("Error getting user growth data: {}", e.getMessage());
            for (int i = 0; i < 7; i++) {
                userData.add(0L);
            }
        }

        return userData;
    }

    private void setDefaultValues(AdminDashboardDTO dto) {
        dto.setTotalUsers(0L);
        dto.setActiveUsers(0L);
        dto.setPendingApprovals(0L);
        dto.setTotalShops(0L);
        dto.setActiveShops(0L);
        dto.setInactiveShops(0L);
        dto.setTotalRevenue(0.0);

        // Default plan stats
        List<PopularPlanDTO> defaultPlans = new ArrayList<>();
        defaultPlans.add(new PopularPlanDTO("FREE", 0L));
        defaultPlans.add(new PopularPlanDTO("BASIC", 0L));
        defaultPlans.add(new PopularPlanDTO("PREMIUM", 0L));
        defaultPlans.add(new PopularPlanDTO("ENTERPRISE", 0L));
        dto.setPopularPlans(defaultPlans);

        dto.setPlanLabels(Arrays.asList("FREE", "BASIC", "PREMIUM", "ENTERPRISE"));
        dto.setPlanData(Arrays.asList(0L, 0L, 0L, 0L));

        dto.setRevenueLabels(getLast7DaysLabels());

        List<Double> defaultRevenue = new ArrayList<>();
        List<Long> defaultUsers = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            defaultRevenue.add(0.0);
            defaultUsers.add(0L);
        }
        dto.setRevenueData(defaultRevenue);
        dto.setUsersData(defaultUsers);

        dto.setRecentShops(new ArrayList<>());
        dto.setRecentUsers(new ArrayList<>());
        dto.setPeriod("Last 7 days");
    }
}