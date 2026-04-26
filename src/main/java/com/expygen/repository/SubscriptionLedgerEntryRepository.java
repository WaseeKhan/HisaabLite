package com.expygen.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionLedgerEntry;

public interface SubscriptionLedgerEntryRepository extends JpaRepository<SubscriptionLedgerEntry, Long> {
    @EntityGraph(attributePaths = {"shop"})
    Page<SubscriptionLedgerEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"shop"})
    List<SubscriptionLedgerEntry> findTop10ByShopOrderByCreatedAtDesc(Shop shop);
}
