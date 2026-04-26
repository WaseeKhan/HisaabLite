package com.expygen.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.expygen.entity.PlanType;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionLedgerEntry;
import com.expygen.entity.UpgradeRequest;
import com.expygen.repository.SubscriptionLedgerEntryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionLedgerService {

    private final SubscriptionLedgerEntryRepository subscriptionLedgerEntryRepository;

    public void recordEntry(Shop shop,
                            UpgradeRequest request,
                            PlanType planType,
                            String entryType,
                            Double amount,
                            Integer durationInDays,
                            LocalDateTime effectiveFrom,
                            LocalDateTime effectiveUntil,
                            String paymentReference,
                            String sourceReference,
                            String note,
                            String createdBy) {
        subscriptionLedgerEntryRepository.save(SubscriptionLedgerEntry.builder()
                .shop(shop)
                .request(request)
                .planType(planType)
                .entryType(entryType)
                .amount(amount)
                .durationInDays(durationInDays)
                .effectiveFrom(effectiveFrom)
                .effectiveUntil(effectiveUntil)
                .paymentReference(paymentReference)
                .sourceReference(sourceReference)
                .note(note)
                .createdBy(createdBy)
                .build());
    }

    public Page<SubscriptionLedgerEntry> getEntries(Pageable pageable) {
        return subscriptionLedgerEntryRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<SubscriptionLedgerEntry> getRecentEntriesForShop(Shop shop) {
        return subscriptionLedgerEntryRepository.findTop10ByShopOrderByCreatedAtDesc(shop);
    }
}
