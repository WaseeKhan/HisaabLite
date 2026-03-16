package com.hisaablite.scheduler;

import com.hisaablite.entity.User;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.SubscriptionPlan;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Role;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.repository.SubscriptionPlanRepository;
import com.hisaablite.repository.ShopRepository; // Add this import
import com.hisaablite.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final ShopRepository shopRepository; // Add this for shop operations
    private final EmailService emailService;

    /**
     * Run daily at midnight to check and update expired subscriptions
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void checkExpiredSubscriptions() {
        log.info("Running expired subscriptions check at {}", LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();
        List<User> expiredUsers = userRepository.findBySubscriptionEndDateBeforeAndApprovedTrue(now);

        int expiredCount = 0;
        for (User user : expiredUsers) {
            try {
                log.info("Subscription expired for user: {} (ID: {}), Plan: {}, Expired on: {}",
                        user.getUsername(), user.getId(), user.getCurrentPlan(), user.getSubscriptionEndDate());

                // Get FREE plan from database
                SubscriptionPlan freePlan = planRepository.findByPlanNameIgnoreCase(PlanType.FREE.name())
                        .orElse(null);

                if (freePlan != null) {
                    // Downgrade user to FREE plan
                    user.setCurrentPlan(PlanType.FREE);
                    user.setSubscriptionEndDate(null); // FREE plan has no expiry

                    // Update user's shop if they are owner
                    if (user.getRole() == Role.OWNER && user.getShop() != null) {
                        Shop shop = user.getShop();
                        shop.setPlanType(PlanType.FREE);
                        shop.setSubscriptionEndDate(null);
                        shopRepository.save(shop);
                        log.info("Downgraded shop {} to FREE plan", shop.getId());
                    }

                    log.info("Downgraded user {} to FREE plan", user.getUsername());
                } else {
                    // If FREE plan not found, block access
                    user.setApproved(false);
                    log.info("Blocked access for user {} (no FREE plan found)", user.getUsername());
                }

                userRepository.save(user);

                // Send expiration email

                SubscriptionPlan oldPlan = planRepository.findByPlanName(user.getCurrentPlan().name()).orElse(null);
                if (oldPlan != null) {
                    emailService.sendSubscriptionExpiredEmail(user, oldPlan);
                }
                expiredCount++;

            } catch (Exception e) {
                log.error("Error processing expired user {}: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("Expired subscriptions check completed. Processed {} expired users.", expiredCount);
    }

    /**
     * Run daily at 9 AM to send expiry reminders (7, 3, and 1 day before)
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendExpiryReminders() {
        log.info("Sending expiry reminders at {}", LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();

        // Send reminders for 7 days before expiry
        sendRemindersForDays(now, 7);

        // Send reminders for 3 days before expiry
        sendRemindersForDays(now, 3);

        // Send reminders for 1 day before expiry
        sendRemindersForDays(now, 1);
    }

    private void sendRemindersForDays(LocalDateTime now, int days) {
        LocalDateTime targetDate = now.plusDays(days);
        LocalDateTime nextDay = targetDate.plusDays(1);

        List<User> expiringUsers = userRepository.findBySubscriptionEndDateBetween(targetDate, nextDay);

        for (User user : expiringUsers) {
            try {
                log.info("Sending {} day expiry reminder to user: {}", days, user.getUsername());
                SubscriptionPlan currentPlan = planRepository.findByPlanName(user.getCurrentPlan().name()).orElse(null);
                emailService.sendExpiryReminderEmail(user, days, currentPlan);
            } catch (Exception e) {
                log.error("Error sending {} day reminder to user {}: {}", days, user.getId(), e.getMessage());
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