package com.hisaablite.admin.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.hisaablite.entity.AuditLog;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void searchShopAuditLogsReturnsOnlyMatchingShopData() {
        auditLogRepository.save(AuditLog.builder()
                .username("owner-a@test.com")
                .userRole("OWNER")
                .shopId(1L)
                .shopName("Shop A")
                .action("PRODUCT_CREATED")
                .entityType("Product")
                .entityId(101L)
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .build());

        auditLogRepository.save(AuditLog.builder()
                .username("owner-b@test.com")
                .userRole("OWNER")
                .shopId(2L)
                .shopName("Shop B")
                .action("PRODUCT_CREATED")
                .entityType("Product")
                .entityId(102L)
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .build());

        var result = auditLogRepository.searchShopAuditLogs(
                1L,
                null,
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("Shop A", result.getContent().get(0).getShopName());
        assertEquals(1L, result.getContent().get(0).getShopId());
    }

    @Test
    void shopScopedCountsAndFiltersWork() {
        auditLogRepository.save(AuditLog.builder()
                .username("owner@test.com")
                .userRole("OWNER")
                .shopId(5L)
                .shopName("Core Shop")
                .action("STAFF_CREATED")
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .build());

        auditLogRepository.save(AuditLog.builder()
                .username("owner@test.com")
                .userRole("OWNER")
                .shopId(5L)
                .shopName("Core Shop")
                .action("STAFF_UPDATED")
                .status("FAILED")
                .timestamp(LocalDateTime.now())
                .build());

        assertEquals(2, auditLogRepository.countByShopId(5L));
        assertEquals(1, auditLogRepository.countFailedActionsByShopId(5L));
        assertEquals(1, auditLogRepository.findDistinctUsernamesByShopId(5L).size());
        assertEquals(2, auditLogRepository.findDistinctActionsByShopId(5L).size());
    }
}
