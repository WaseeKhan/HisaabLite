package com.expygen.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionLedgerEntry;

public interface SubscriptionLedgerEntryRepository extends JpaRepository<SubscriptionLedgerEntry, Long> {
    @EntityGraph(attributePaths = {"shop"})
    Page<SubscriptionLedgerEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"shop"})
    List<SubscriptionLedgerEntry> findTop10ByShopOrderByCreatedAtDesc(Shop shop);

    long countByShop(Shop shop);

    @Query("SELECT COUNT(DISTINCT e.shop.id) FROM SubscriptionLedgerEntry e")
    long countDistinctShops();

    @Query("SELECT COUNT(e) FROM SubscriptionLedgerEntry e WHERE e.entryType IN :entryTypes")
    long countByEntryTypeIn(@Param("entryTypes") Collection<String> entryTypes);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM SubscriptionLedgerEntry e WHERE e.amount IS NOT NULL")
    Double sumTrackedAmount();
}
