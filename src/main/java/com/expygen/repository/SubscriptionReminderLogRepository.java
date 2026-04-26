package com.expygen.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionReminderLog;

public interface SubscriptionReminderLogRepository extends JpaRepository<SubscriptionReminderLog, Long> {
    Optional<SubscriptionReminderLog> findFirstByShopAndReminderTypeAndTargetDate(Shop shop, String reminderType, LocalDateTime targetDate);
}
