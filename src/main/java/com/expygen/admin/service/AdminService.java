package com.expygen.admin.service;

import com.expygen.admin.dto.AdminDashboardDTO;
import com.expygen.admin.dto.PopularPlanDTO;
import com.expygen.admin.repository.AuditLogRepository;
import com.expygen.admin.repository.AdminShopRepository;
import com.expygen.admin.repository.AdminSubscriptionRepository;
import com.expygen.admin.repository.AdminUserRepository;
import com.expygen.entity.AuditLog;
import com.expygen.entity.ContactRequest;
import com.expygen.entity.ContactRequestStatus;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.TicketPriority;
import com.expygen.entity.TicketStatus;
import com.expygen.entity.UpgradeRequest;
import com.expygen.entity.UpgradeRequestStatus;
import com.expygen.entity.User;
import com.expygen.repository.ContactRequestRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.SupportTicketRepository;
import com.expygen.repository.UpgradeRequestRepository;
import com.expygen.service.SubscriptionLifecycleService;
import com.expygen.service.SubscriptionLifecycleStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
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
    private final AdminSubscriptionRepository adminSubscriptionRepo;
    private final SupportTicketRepository supportTicketRepository;
    private final SaleRepository saleRepository;
    private final AuditLogRepository auditLogRepository;
    private final UpgradeRequestRepository upgradeRequestRepository;
    private final ContactRequestRepository contactRequestRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

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
            dto.setTotalInvoices(saleRepository.count());
            dto.setOpenTickets(supportTicketRepository.countByStatus(TicketStatus.OPEN));
            dto.setInProgressTickets(supportTicketRepository.countByStatus(TicketStatus.IN_PROGRESS));
            dto.setResolvedTickets(supportTicketRepository.countByStatus(TicketStatus.RESOLVED));
            dto.setClosedTickets(supportTicketRepository.countByStatus(TicketStatus.CLOSED));
            dto.setUrgentTickets(supportTicketRepository.countByPriority(TicketPriority.URGENT));
            dto.setTotalTickets(supportTicketRepository.count());

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime last30Days = now.minusDays(30);
            LocalDateTime previous30Days = now.minusDays(60);
            LocalDateTime last7Days = now.minusDays(7);
            LocalDateTime previous7Days = now.minusDays(14);
            List<Shop> allShops = adminShopRepo.findAll();
            Map<String, SubscriptionPlan> planConfig = getPlanConfigByName();

            fillCommercialMetrics(dto, allShops, planConfig, last30Days, previous30Days, now);
            fillGrowthMetrics(dto, last7Days, previous7Days, now);
            fillOperationalHealthMetrics(dto, allShops, now);

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
            dto.setPlanMixBreakdown(buildPlanMixBreakdown(planStats, dto.getTotalShops()));

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
                shopMap.put("mrr", resolveMonthlyPlanValue(shop.getPlanType(), planConfig));
                recentShops.add(shopMap);
            }
            dto.setRecentShops(recentShops);

            // ===== RECENT USERS =====
            Map<String, LocalDateTime> lastActivityByUsername = getLastActivityByUsername();
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
                userMap.put("lastActiveLabel", formatRelativeTime(lastActivityByUsername.get(user.getUsername())));
                recentUsers.add(userMap);
            }
            dto.setRecentUsers(recentUsers);

            // ===== CHART DATA =====
            dto.setRevenueLabels(getLast7DaysLabels());
            dto.setRevenueData(getRevenueTrendData(allShops, last7Days));
            dto.setUsersData(getUserGrowthData());
            dto.setTicketData(getTicketTrendData());
            dto.setTicketLabels(getLast7DaysLabels());
            dto.setRevenueBreakdown(buildRevenueBreakdown(allShops));
            dto.setRecentActivities(buildRecentActivities());
            dto.setAlertItems(buildAlertItems());
            dto.setMonitorSideMetrics(buildMonitorSideMetrics(dto));
            dto.setMonitorFooterMetrics(buildMonitorFooterMetrics(dto));
            dto.setHealthMixRows(buildHealthMixRows(dto));
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
        return adminUserRepo.findFirstByShopAndRoleOrderByIdAsc(shop, Role.OWNER)
                .map(User::getName)
                .orElse("N/A");
    }

    private List<PopularPlanDTO> getPlanStatistics() {
        List<PopularPlanDTO> planStats = new ArrayList<>();

        try {
            // Initialize counters
            Map<String, Long> counts = new HashMap<>();
            counts.put("FREE", 0L);
            counts.put("BASIC", 0L);
            counts.put("PRO", 0L);

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
            List<String> order = Arrays.asList("FREE", "BASIC", "PRO");
            planStats.sort(Comparator.comparingInt(a -> order.indexOf(a.getPlanName())));

        } catch (Exception e) {
            log.error("Error getting plan stats: {}", e.getMessage());
            // Default values
            planStats.add(new PopularPlanDTO("FREE", 0L));
            planStats.add(new PopularPlanDTO("BASIC", 0L));
            planStats.add(new PopularPlanDTO("PRO", 0L));
        }

        return planStats;
    }

    private Map<String, SubscriptionPlan> getPlanConfigByName() {
        Map<String, SubscriptionPlan> plans = new HashMap<>();
        for (SubscriptionPlan plan : adminSubscriptionRepo.findAll()) {
            if (plan.getPlanName() != null) {
                plans.put(plan.getPlanName().toUpperCase(Locale.ROOT), plan);
            }
        }
        return plans;
    }

    private void fillCommercialMetrics(AdminDashboardDTO dto,
                                       List<Shop> allShops,
                                       Map<String, SubscriptionPlan> planConfig,
                                       LocalDateTime last30Days,
                                       LocalDateTime previous30Days,
                                       LocalDateTime now) {
        double totalRevenue = 0.0d;
        double currentRevenue = 0.0d;
        double previousRevenue = 0.0d;
        double recurringRevenue = 0.0d;
        long paidShops = 0L;
        long newShops = 0L;

        for (Shop shop : allShops) {
            BigDecimal lifetimeRevenue = saleRepository.getTotalRevenueByShop(shop);
            totalRevenue += lifetimeRevenue != null ? lifetimeRevenue.doubleValue() : 0.0d;

            Double currentPeriodRevenue = saleRepository.getTodayCompletedRevenue(shop, last30Days, now);
            currentRevenue += currentPeriodRevenue != null ? currentPeriodRevenue : 0.0d;

            Double previousPeriodRevenue = saleRepository.getTodayCompletedRevenue(shop, previous30Days, last30Days);
            previousRevenue += previousPeriodRevenue != null ? previousPeriodRevenue : 0.0d;

            if (shop.getCreatedAt() != null && !shop.getCreatedAt().isBefore(last30Days)) {
                newShops++;
            }

            if (shop.isActive() && shop.getPlanType() != null && shop.getPlanType() != PlanType.FREE) {
                paidShops++;
                recurringRevenue += resolveMonthlyPlanValue(shop.getPlanType(), planConfig);
            }
        }

        dto.setTotalRevenue(roundToTwo(totalRevenue));
        dto.setRevenueGrowthPercent(calculateGrowthPercent(currentRevenue, previousRevenue));
        dto.setMonthlyRecurringRevenue(roundToTwo(recurringRevenue));
        dto.setPaidShops(paidShops);
        dto.setNewShopsLast30Days(newShops);
    }

    private void fillGrowthMetrics(AdminDashboardDTO dto,
                                   LocalDateTime last7Days,
                                   LocalDateTime previous7Days,
                                   LocalDateTime now) {
        long currentUsers = sumCounts(adminUserRepo.getUserStatsByDate(last7Days, now));
        long previousUsers = sumCounts(adminUserRepo.getUserStatsByDate(previous7Days, last7Days));
        dto.setNewUsersLast7Days(currentUsers);
        dto.setUserGrowthPercent(calculateGrowthPercent(currentUsers, previousUsers));

        Long currentTickets = supportTicketRepository.countByCreatedAtBetween(last7Days, now);
        Long previousTickets = supportTicketRepository.countByCreatedAtBetween(previous7Days, last7Days);
        dto.setTicketTrendPercent(calculateGrowthPercent(
                currentTickets != null ? currentTickets : 0L,
                previousTickets != null ? previousTickets : 0L));
    }

    private void fillOperationalHealthMetrics(AdminDashboardDTO dto,
                                              List<Shop> allShops,
                                              LocalDateTime now) {
        long renewalDueShops = 0L;
        long gracePeriodShops = 0L;
        long expiredLifecycleShops = 0L;
        long disconnectedWhatsappShops = 0L;
        long dormantShops = 0L;

        for (Shop shop : allShops) {
            SubscriptionLifecycleStatus status = subscriptionLifecycleService.buildSnapshot(shop).getStatus();
            if (status == SubscriptionLifecycleStatus.RENEWAL_DUE) {
                renewalDueShops++;
            } else if (status == SubscriptionLifecycleStatus.GRACE_PERIOD) {
                gracePeriodShops++;
            } else if (status == SubscriptionLifecycleStatus.EXPIRED) {
                expiredLifecycleShops++;
            }

            boolean whatsappConfigured = (shop.getWhatsappNumber() != null && !shop.getWhatsappNumber().isBlank())
                    || shop.isWhatsappAdminDisabled();
            if (whatsappConfigured && (!shop.isWhatsappConnected() || shop.isWhatsappAdminDisabled())) {
                disconnectedWhatsappShops++;
            }

            if (shop.isActive() && saleRepository.countByShopAndSaleDateAfter(shop, now.minusDays(30)) == 0) {
                dormantShops++;
            }
        }

        long requestedCommercialCount = upgradeRequestRepository.countByStatus(UpgradeRequestStatus.REQUESTED);
        long contactedCommercialCount = upgradeRequestRepository.countByStatus(UpgradeRequestStatus.CONTACTED);
        long paymentReceivedCommercialCount = upgradeRequestRepository.countByStatus(UpgradeRequestStatus.PAYMENT_RECEIVED);
        long totalCommercialRequests = requestedCommercialCount + contactedCommercialCount + paymentReceivedCommercialCount;
        long failedOps = auditLogRepository.countFailedActions();
        long overdueTickets = supportTicketRepository.countOverdueTickets(now);
        long newContactRequests = contactRequestRepository.countByStatus(ContactRequestStatus.NEW);

        dto.setRenewalDueShops(renewalDueShops);
        dto.setGracePeriodShops(gracePeriodShops);
        dto.setExpiredLifecycleShops(expiredLifecycleShops);
        dto.setDisconnectedWhatsappShops(disconnectedWhatsappShops);
        dto.setDormantShops(dormantShops);
        dto.setRequestedCommercialCount(requestedCommercialCount);
        dto.setContactedCommercialCount(contactedCommercialCount);
        dto.setPaymentReceivedCommercialCount(paymentReceivedCommercialCount);
        dto.setTotalCommercialRequests(totalCommercialRequests);
        dto.setPlatformRiskCount(failedOps);
        dto.setOverdueTickets(overdueTickets);
        dto.setNewContactRequests(newContactRequests);

        long totalShops = dto.getTotalShops();
        double operationalPercent = totalShops > 0
                ? ((double) Math.max(0L, dto.getActiveShops() - renewalDueShops - gracePeriodShops - expiredLifecycleShops) / totalShops) * 100.0d
                : 0.0d;
        double warningPercent = totalShops > 0
                ? ((double) (renewalDueShops + gracePeriodShops) / totalShops) * 100.0d
                : 0.0d;
        double criticalPercent = totalShops > 0
                ? ((double) expiredLifecycleShops / totalShops) * 100.0d
                : 0.0d;

        dto.setOperationalHealthPercent(roundToTwo(operationalPercent));
        dto.setWarningHealthPercent(roundToTwo(warningPercent));
        dto.setCriticalHealthPercent(roundToTwo(criticalPercent));
    }

    private List<Map<String, Object>> buildRevenueBreakdown(List<Shop> allShops) {
        Map<String, Double> revenueByPlan = new LinkedHashMap<>();
        revenueByPlan.put("FREE", 0.0d);
        revenueByPlan.put("BASIC", 0.0d);
        revenueByPlan.put("PRO", 0.0d);

        double total = 0.0d;
        for (Shop shop : allShops) {
            String planName = shop.getPlanType() != null ? shop.getPlanType().name() : "FREE";
            double revenue = Optional.ofNullable(saleRepository.getTotalRevenueByShop(shop))
                    .map(BigDecimal::doubleValue)
                    .orElse(0.0d);
            revenueByPlan.put(planName, revenueByPlan.getOrDefault(planName, 0.0d) + revenue);
            total += revenue;
        }

        List<String> tones = List.of("purple", "blue", "green");
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Double> entry : revenueByPlan.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("label", entry.getKey());
            row.put("amount", roundToTwo(entry.getValue()));
            row.put("percentage", total > 0 ? roundToTwo((entry.getValue() * 100.0d) / total) : 0.0d);
            row.put("tone", tones.get(Math.min(index, tones.size() - 1)));
            rows.add(row);
            index++;
        }
        return rows;
    }

    private List<Map<String, Object>> buildPlanMixBreakdown(List<PopularPlanDTO> planStats, long totalShops) {
        List<String> tones = List.of("purple", "blue", "green");
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (PopularPlanDTO plan : planStats) {
            Map<String, Object> row = new HashMap<>();
            row.put("label", plan.getPlanName());
            row.put("count", plan.getShopCount());
            row.put("percentage", totalShops > 0 ? roundToTwo((plan.getShopCount() * 100.0d) / totalShops) : 0.0d);
            row.put("tone", tones.get(Math.min(index, tones.size() - 1)));
            rows.add(row);
            index++;
        }
        return rows;
    }

    private Map<String, LocalDateTime> getLastActivityByUsername() {
        Map<String, LocalDateTime> lastActivity = new HashMap<>();
        for (Object[] row : auditLogRepository.getUserActivitySummary(org.springframework.data.domain.PageRequest.of(0, 25))) {
            if (row[0] instanceof String username && row[2] instanceof LocalDateTime timestamp) {
                lastActivity.put(username, timestamp);
            }
        }
        return lastActivity;
    }

    private List<Map<String, Object>> buildRecentActivities() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AuditLog auditLog : auditLogRepository.findRecentActivities(org.springframework.data.domain.PageRequest.of(0, 5))) {
            Map<String, Object> row = new HashMap<>();
            row.put("iconClass", resolveAuditIcon(auditLog.getAction()));
            row.put("toneClass", "blue-text");
            row.put("text", (auditLog.getAction() != null ? auditLog.getAction().replace('_', ' ') : "Activity")
                    + (auditLog.getShopName() != null ? " • " + auditLog.getShopName() : ""));
            row.put("meta", formatRelativeTime(auditLog.getTimestamp()));
            items.add(row);
        }
        return items;
    }

    private List<Map<String, Object>> buildAlertItems() {
        List<Map<String, Object>> items = new ArrayList<>();

        List<AuditLog> failedLogs = auditLogRepository.findByStatusOrderByTimestampDesc("FAILED");
        for (int i = 0; i < Math.min(2, failedLogs.size()); i++) {
            AuditLog logEntry = failedLogs.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("iconClass", "fa-solid fa-triangle-exclamation");
            item.put("title", logEntry.getAction() != null ? logEntry.getAction().replace('_', ' ') : "Failed operation");
            item.put("detail", logEntry.getDetails() != null ? logEntry.getDetails() : "Recent failed administrative action detected.");
            item.put("timeLabel", formatRelativeTime(logEntry.getTimestamp()));
            items.add(item);
        }

        upgradeRequestRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 5)).stream()
                .filter(request -> request.getStatus() == UpgradeRequestStatus.PAYMENT_RECEIVED)
                .findFirst()
                .ifPresent(request -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("iconClass", "fa-solid fa-wallet");
                    item.put("title", "Payment received");
                    item.put("detail", request.getShop().getName() + " • " + request.getRequestedPlan());
                    item.put("timeLabel", formatRelativeTime(request.getPaymentReceivedAt() != null ? request.getPaymentReceivedAt() : request.getCreatedAt()));
                    items.add(item);
                });

        contactRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(contact -> contact.getStatus() == ContactRequestStatus.NEW)
                .findFirst()
                .ifPresent(contact -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("iconClass", "fa-solid fa-bell");
                    item.put("title", "New contact request");
                    item.put("detail", contact.getStoreName() + " • " + contact.getTopic());
                    item.put("timeLabel", formatRelativeTime(contact.getCreatedAt()));
                    items.add(item);
                });

        return items.stream().limit(4).toList();
    }

    private List<Map<String, Object>> buildMonitorSideMetrics(AdminDashboardDTO dto) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildMetric("Renewal Due", String.valueOf(dto.getRenewalDueShops()), "warning"));
        items.add(buildMetric("Grace Period", String.valueOf(dto.getGracePeriodShops()), "warning"));
        items.add(buildMetric("Expired", String.valueOf(dto.getExpiredLifecycleShops()), "critical"));
        items.add(buildMetric("Pending Approvals", String.valueOf(dto.getPendingApprovals()), "info"));
        items.add(buildMetric("New Leads", String.valueOf(dto.getNewContactRequests()), "info"));
        items.add(buildMetric("Failed Ops", String.valueOf(dto.getPlatformRiskCount()), "critical"));
        return items;
    }

    private List<Map<String, Object>> buildMonitorFooterMetrics(AdminDashboardDTO dto) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildMetric("Commercial Queue", String.valueOf(dto.getTotalCommercialRequests()), "warning"));
        items.add(buildMetric("Urgent Tickets", String.valueOf(dto.getUrgentTickets() != null ? dto.getUrgentTickets() : 0L), "critical"));
        items.add(buildMetric("Dormant Shops", String.valueOf(dto.getDormantShops()), "info"));
        items.add(buildMetric("WhatsApp Risk", String.valueOf(dto.getDisconnectedWhatsappShops()), "warning"));
        return items;
    }

    private List<Map<String, Object>> buildHealthMixRows(AdminDashboardDTO dto) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(buildHealthRow("Operational", dto.getOperationalHealthPercent(), Math.max(0L, dto.getActiveShops() - dto.getRenewalDueShops() - dto.getGracePeriodShops() - dto.getExpiredLifecycleShops()), "green"));
        rows.add(buildHealthRow("Attention", dto.getWarningHealthPercent(), dto.getRenewalDueShops() + dto.getGracePeriodShops(), "amber"));
        rows.add(buildHealthRow("Critical", dto.getCriticalHealthPercent(), dto.getExpiredLifecycleShops(), "red"));
        return rows;
    }

    private Map<String, Object> buildMetric(String label, String value, String tone) {
        Map<String, Object> item = new HashMap<>();
        item.put("label", label);
        item.put("value", value);
        item.put("tone", tone);
        return item;
    }

    private Map<String, Object> buildHealthRow(String label, double percentage, long count, String tone) {
        Map<String, Object> row = new HashMap<>();
        row.put("label", label);
        row.put("percentage", roundToTwo(percentage));
        row.put("count", count);
        row.put("tone", tone);
        return row;
    }

    private double resolveMonthlyPlanValue(PlanType planType, Map<String, SubscriptionPlan> planConfig) {
        if (planType == null || planType == PlanType.FREE) {
            return 0.0d;
        }
        SubscriptionPlan plan = planConfig.get(planType.name());
        if (plan == null) {
            return 0.0d;
        }
        if (plan.getAnnualPrice() != null && plan.getAnnualPrice() > 0) {
            return roundToTwo(plan.getAnnualPrice() / 12.0d);
        }
        return roundToTwo(plan.getPrice() != null ? plan.getPrice() : 0.0d);
    }

    private long sumCounts(List<Object[]> rows) {
        long total = 0L;
        for (Object[] row : rows) {
            if (row != null && row.length > 1 && row[1] instanceof Number number) {
                total += number.longValue();
            }
        }
        return total;
    }

    private double calculateGrowthPercent(double current, double previous) {
        if (previous == 0.0d) {
            return current > 0.0d ? 100.0d : 0.0d;
        }
        return roundToTwo(((current - previous) / previous) * 100.0d);
    }

    private double roundToTwo(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String formatRelativeTime(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "No recent activity";
        }

        Duration duration = Duration.between(timestamp, LocalDateTime.now());
        long minutes = Math.max(0L, duration.toMinutes());
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " hr ago";
        }
        long days = duration.toDays();
        if (days < 7) {
            return days + " day ago";
        }
        return timestamp.format(DateTimeFormatter.ofPattern("dd MMM"));
    }

    private String resolveAuditIcon(String action) {
        if (action == null) {
            return "fa-solid fa-circle-info";
        }
        String normalized = action.toUpperCase(Locale.ROOT);
        if (normalized.contains("PAYMENT") || normalized.contains("LEDGER")) {
            return "fa-solid fa-wallet";
        }
        if (normalized.contains("SUBSCRIPTION") || normalized.contains("PLAN")) {
            return "fa-solid fa-arrow-up";
        }
        if (normalized.contains("USER")) {
            return "fa-solid fa-user-plus";
        }
        if (normalized.contains("SHOP")) {
            return "fa-solid fa-store";
        }
        return "fa-solid fa-database";
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

    private List<Double> getRevenueTrendData(List<Shop> allShops, LocalDateTime start) {
        Map<LocalDate, Double> totalsByDate = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            totalsByDate.put(today.minusDays(i), 0.0d);
        }

        for (Shop shop : allShops) {
            List<Object[]> rows = saleRepository.getLast7DaysRevenue(shop, start);
            for (Object[] row : rows) {
                if (row[0] == null || row[1] == null) {
                    continue;
                }
                LocalDate date = parseResultDate(row[0]);
                if (date == null || !totalsByDate.containsKey(date)) {
                    continue;
                }
                totalsByDate.put(date, totalsByDate.get(date) + ((Number) row[1]).doubleValue());
            }
        }

        return totalsByDate.values().stream().map(this::roundToTwo).toList();
    }

    private LocalDate parseResultDate(Object rawDate) {
        if (rawDate instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (rawDate instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (rawDate instanceof LocalDate localDate) {
            return localDate;
        }
        if (rawDate instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        return null;
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

   
    private List<Long> getTicketTrendData() {
        List<Long> ticketData = new ArrayList<>();
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(7);

            // Get daily ticket counts
            LocalDate today = LocalDate.now();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
                
                Long count = supportTicketRepository.countByCreatedAtBetween(dayStart, dayEnd);
                ticketData.add(count != null ? count : 0L);
            }

        } catch (Exception e) {
            log.error("Error getting ticket trend data: {}", e.getMessage());
            for (int i = 0; i < 7; i++) {
                ticketData.add(0L);
            }
        }
        return ticketData;
    }

    private void setDefaultValues(AdminDashboardDTO dto) {
        dto.setTotalUsers(0L);
        dto.setActiveUsers(0L);
        dto.setPendingApprovals(0L);
        dto.setTotalShops(0L);
        dto.setActiveShops(0L);
        dto.setInactiveShops(0L);
        dto.setTotalRevenue(0.0);
        dto.setTotalInvoices(0L);

        // Default plan stats
        List<PopularPlanDTO> defaultPlans = new ArrayList<>();
        defaultPlans.add(new PopularPlanDTO("FREE", 0L));
        defaultPlans.add(new PopularPlanDTO("BASIC", 0L));
        defaultPlans.add(new PopularPlanDTO("PRO", 0L));
        dto.setPopularPlans(defaultPlans);

        dto.setPlanLabels(Arrays.asList("FREE", "BASIC", "PRO"));
        dto.setPlanData(Arrays.asList(0L, 0L, 0L));

        dto.setRevenueLabels(getLast7DaysLabels());

        List<Double> defaultRevenue = new ArrayList<>();
        List<Long> defaultUsers = new ArrayList<>();
        List<Long> defaultTicketData = new ArrayList<>();
        
        for (int i = 0; i < 7; i++) {
            defaultRevenue.add(0.0);
            defaultUsers.add(0L);
            defaultTicketData.add(0L);
        }
        dto.setRevenueData(defaultRevenue);
        dto.setUsersData(defaultUsers);
        dto.setTicketData(defaultTicketData);  

        dto.setRecentShops(new ArrayList<>());
        dto.setRecentUsers(new ArrayList<>());
        dto.setRevenueBreakdown(new ArrayList<>());
        dto.setPlanMixBreakdown(new ArrayList<>());
        dto.setRecentActivities(new ArrayList<>());
        dto.setAlertItems(new ArrayList<>());
        dto.setMonitorSideMetrics(new ArrayList<>());
        dto.setMonitorFooterMetrics(new ArrayList<>());
        dto.setHealthMixRows(new ArrayList<>());
        dto.setPeriod("Last 7 days");
    }

    public Map<String, Long> getTicketStats() {
        Map<String, Long> stats = new HashMap<>();
        
        try {
            stats.put("totalTickets", supportTicketRepository.count());
            stats.put("openTickets", supportTicketRepository.countByStatus(TicketStatus.OPEN));
            stats.put("inProgressTickets", supportTicketRepository.countByStatus(TicketStatus.IN_PROGRESS));
            stats.put("resolvedTickets", supportTicketRepository.countByStatus(TicketStatus.RESOLVED));
            stats.put("closedTickets", supportTicketRepository.countByStatus(TicketStatus.CLOSED));
            stats.put("urgentTickets", supportTicketRepository.countByPriority(TicketPriority.URGENT));
        } catch (Exception e) {
            log.error("Error getting ticket stats: {}", e.getMessage());
            // Default values
            stats.put("totalTickets", 0L);
            stats.put("openTickets", 0L);
            stats.put("inProgressTickets", 0L);
            stats.put("resolvedTickets", 0L);
            stats.put("closedTickets", 0L);
            stats.put("urgentTickets", 0L);
        }
        
        return stats;
    }
}
