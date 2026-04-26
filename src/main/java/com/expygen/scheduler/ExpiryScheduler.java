package com.expygen.scheduler;

import com.expygen.config.AppConfig;
import com.expygen.dto.SubscriptionLifecycleSnapshot;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.User;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.SubscriptionReminderLogRepository;
import com.expygen.repository.UserRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.service.EmailService;
import com.expygen.service.SubscriptionLifecycleService;
import com.expygen.service.SubscriptionLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionReminderLogRepository subscriptionReminderLogRepository;
    private final EmailService emailService;
    private final AppConfig appConfig;

    /**
     * Run daily at midnight to check and update expired subscriptions
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void checkExpiredSubscriptions() {
        log.info("Running expired subscriptions check at {}", LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();
        List<Shop> shopsPastEndDate = shopRepository.findShopsWithExpiredSubscription(now);

        int graceCount = 0;
        int blockedCount = 0;
        for (Shop shop : shopsPastEndDate) {
            try {
                SubscriptionLifecycleSnapshot snapshot = subscriptionLifecycleService.buildSnapshot(shop);
                syncUsersWithShop(shop);

                if (snapshot.getStatus() == SubscriptionLifecycleStatus.GRACE_PERIOD) {
                    graceCount++;
                    log.info("Shop {} is in grace period with {} day(s) remaining. Plan={}, ends={}",
                            shop.getId(),
                            snapshot.getGraceDaysRemaining(),
                            shop.getPlanType(),
                            shop.getSubscriptionEndDate());
                } else if (snapshot.getStatus() == SubscriptionLifecycleStatus.EXPIRED) {
                    blockedCount++;
                    log.info("Shop {} is fully expired. Workspace remains blocked until manual renewal activation. Plan={}, ended={}",
                            shop.getId(),
                            shop.getPlanType(),
                            shop.getSubscriptionEndDate());
                }

            } catch (Exception e) {
                log.error("Error processing expired shop {}: {}", shop.getId(), e.getMessage(), e);
            }
        }

        log.info("Expired subscriptions check completed. Grace period shops: {}, blocked expired shops: {}",
                graceCount, blockedCount);
    }

    /**
     * Run daily at 9 AM to send expiry reminders (7, 3, and 1 day before)
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendExpiryReminders() {
        if (!appConfig.isAutomatedSubscriptionRemindersEnabled()) {
            log.info("Automatic expiry reminders are disabled by configuration.");
            return;
        }

        log.info("Running automatic expiry reminder scheduler");
        LocalDateTime now = LocalDateTime.now();
        Arrays.asList(appConfig.getSubscriptionRenewalReminderDays(), 7, 1).stream()
                .distinct()
                .filter(days -> days > 0)
                .forEach(days -> sendRemindersForDays(now, days));
    }

    private void sendRemindersForDays(LocalDateTime now, int days) {
        List<Shop> shops = shopRepository.findShopsWithExpiringSubscription(
                now.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(days),
                now.withHour(23).withMinute(59).withSecond(59).withNano(0).plusDays(days));

        for (Shop shop : shops) {
            try {
                User owner = userRepository.findFirstByShopAndRole(shop, Role.OWNER).orElse(null);
                if (owner == null || owner.getSubscriptionEndDate() == null) {
                    continue;
                }

                LocalDateTime targetDate = owner.getSubscriptionEndDate().withHour(0).withMinute(0).withSecond(0).withNano(0);
                String reminderType = "EXPIRY_" + days + "_DAY";
                if (subscriptionReminderLogRepository.findFirstByShopAndReminderTypeAndTargetDate(shop, reminderType, targetDate).isPresent()) {
                    continue;
                }

                SubscriptionPlan plan = subscriptionPlanRepository.findByPlanNameIgnoreCase(
                        shop.getPlanType() != null ? shop.getPlanType().name() : "FREE").orElse(null);
                if (plan == null) {
                    continue;
                }

                emailService.sendExpiryReminderEmail(owner, days, plan);
                subscriptionReminderLogRepository.save(com.expygen.entity.SubscriptionReminderLog.builder()
                        .shop(shop)
                        .reminderType(reminderType)
                        .targetDate(targetDate)
                        .build());
            } catch (Exception ex) {
                log.error("Could not send {}-day reminder for shop {}: {}", days, shop.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Run every hour to check for recently approved users and sync shop data
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void syncApprovedUsersWithShops() {
        log.info("Syncing approved users with shops");

        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<User> recentlyApproved = userRepository.findByApprovedTrueAndApprovalDateAfter(twentyFourHoursAgo);

        int syncCount = 0;
        for (User user : recentlyApproved) {
            try {
                if (user.getRole() == Role.OWNER && user.getShop() != null) {
                    Shop shop = user.getShop();

                    // Update shop with user's subscription details
                    shop.setPlanType(user.getCurrentPlan());
                    shop.setSubscriptionStartDate(user.getApprovalDate());
                    shop.setSubscriptionEndDate(user.getSubscriptionEndDate());

                    shopRepository.save(shop);
                    log.info("Synced shop {} for user {} (Plan: {})",
                            shop.getId(), user.getUsername(), user.getCurrentPlan());
                    syncCount++;
                }
            } catch (Exception e) {
                log.error("Error syncing user {} with shop: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("Sync completed. Updated {} shops.", syncCount);
    }

    private void syncUsersWithShop(Shop shop) {
        List<User> users = userRepository.findByShop(shop);
        boolean changed = false;

        for (User user : users) {
            if (user.getCurrentPlan() != shop.getPlanType()) {
                user.setCurrentPlan(shop.getPlanType());
                changed = true;
            }
            if (!equalsDateTime(user.getSubscriptionStartDate(), shop.getSubscriptionStartDate())) {
                user.setSubscriptionStartDate(shop.getSubscriptionStartDate());
                changed = true;
            }
            if (!equalsDateTime(user.getSubscriptionEndDate(), shop.getSubscriptionEndDate())) {
                user.setSubscriptionEndDate(shop.getSubscriptionEndDate());
                changed = true;
            }
            if (user.getRole() == Role.OWNER && !user.isApproved()) {
                user.setApproved(true);
                changed = true;
            }
        }

        if (changed) {
            userRepository.saveAll(users);
        }
    }

    private boolean equalsDateTime(LocalDateTime left, LocalDateTime right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * Run weekly on Monday to generate subscription reports
     */
    @Scheduled(cron = "0 0 10 * * MON")
    @Transactional
    public void generateSubscriptionReport() {
        log.info("Generating weekly subscription report");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        // Get new subscriptions in last 7 days
        List<User> newSubscriptions = userRepository.findByApprovalDateBetween(sevenDaysAgo, now);

        // Get expiring in next 7 days
        List<User> expiringSoon = userRepository.findBySubscriptionEndDateBetween(now, now.plusDays(7));

        // Get expired in last 7 days
        List<User> expired = userRepository.findBySubscriptionEndDateBetween(sevenDaysAgo, now);

        log.info("Weekly Report - New: {}, Expiring: {}, Expired: {}",
                newSubscriptions.size(), expiringSoon.size(), expired.size());

        // You can send this report to admin email
        // emailService.sendWeeklyReport(newSubscriptions.size(), expiringSoon.size(),
        // expired.size());
    }
}
