package com.expygen.insights.service;

import com.expygen.admin.repository.AuditLogRepository;
import com.expygen.entity.AuditLog;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.ActivityAuditRowDto;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.insights.dto.SalesTrendPointDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditInsightsService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLog> findAuditLogs(Shop shop, LocalDate fromDate, LocalDate toDate, String module, String userName, String keyword) {
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime to = toDate != null ? toDate.plusDays(1).atStartOfDay() : LocalDate.now().plusDays(1).atStartOfDay();

        return auditLogRepository.findAll().stream()
                .filter(log -> log.getShopId() != null && shop.getId().equals(log.getShopId()))
                .filter(log -> log.getTimestamp() != null && !log.getTimestamp().isBefore(from) && log.getTimestamp().isBefore(to))
                .filter(log -> isShopOperationalModule(resolveModule(log)))
                .filter(log -> isBlank(module) || resolveModule(log).equalsIgnoreCase(module))
                .filter(log -> isBlank(userName) || contains(log.getUsername(), userName))
                .filter(log -> isBlank(keyword)
                        || contains(log.getAction(), keyword)
                        || contains(log.getEntityType(), keyword)
                        || contains(log.getDetails(), keyword))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .toList();
    }

    public List<InsightsSummaryCardDto> buildKpis(List<AuditLog> logs) {
        long total = logs.size();
        long billing = logs.stream().filter(log -> "BILLING".equals(resolveModule(log))).count();
        long inventory = logs.stream().filter(log -> "INVENTORY".equals(resolveModule(log))).count();
        long purchase = logs.stream().filter(log -> "PURCHASE".equals(resolveModule(log))).count();
        long high = logs.stream().filter(log -> "FAILED".equalsIgnoreCase(log.getStatus()) || actionLooksCritical(log)).count();
        long users = logs.stream().map(AuditLog::getUsername).filter(v -> v != null && !v.isBlank()).distinct().count();

        return List.of(
                new InsightsSummaryCardDto("Total Activities", String.valueOf(total), "Recorded business and operational events"),
                new InsightsSummaryCardDto("Billing Actions", String.valueOf(billing), "Sales, invoice, and billing related events"),
                new InsightsSummaryCardDto("Inventory Actions", String.valueOf(inventory), "Stock updates, adjustments, and item changes"),
                new InsightsSummaryCardDto("Purchase Actions", String.valueOf(purchase), "Procurement and inward-related activity"),
                new InsightsSummaryCardDto("High Priority Events", String.valueOf(high), "Actions requiring special administrative attention"),
                new InsightsSummaryCardDto("Active Users", String.valueOf(users), "Users with tracked activity in selected range")
        );
    }

    public List<SalesTrendPointDto> buildTrend(List<AuditLog> logs) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        return logs.stream()
                .collect(Collectors.groupingBy(log -> log.getTimestamp().toLocalDate(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SalesTrendPointDto(entry.getKey().format(formatter), entry.getValue().doubleValue()))
                .toList();
    }

    public List<PaymentModeSummaryDto> buildModuleDistribution(List<AuditLog> logs) {
        return logs.stream()
                .collect(Collectors.groupingBy(this::resolveModule, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    public List<PaymentModeSummaryDto> buildTopUsers(List<AuditLog> logs) {
        return logs.stream()
                .collect(Collectors.groupingBy(log -> safe(log.getUsername(), "System"), Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    public List<ActivityAuditRowDto> buildRows(List<AuditLog> logs) {
        return logs.stream()
                .map(log -> new ActivityAuditRowDto(
                        log.getId(),
                        log.getTimestamp(),
                        safe(log.getUsername(), "System"),
                        resolveModule(log),
                        safe(log.getAction(), "Unknown"),
                        buildReference(log),
                        safe(log.getDetails(), "-"),
                        resolvePriority(log)
                ))
                .toList();
    }

    private String buildReference(AuditLog log) {
        if (log.getEntityType() != null && log.getEntityId() != null) {
            return log.getEntityType().toUpperCase(Locale.ENGLISH) + "-" + log.getEntityId();
        }
        if (log.getEntityId() != null) return String.valueOf(log.getEntityId());
        return "-";
    }

    private String resolveModule(AuditLog log) {
        String action = safe(log.getAction(), "").toUpperCase(Locale.ENGLISH);
        String entityType = safe(log.getEntityType(), "").toUpperCase(Locale.ENGLISH);
        String details = safe(log.getDetails(), "").toUpperCase(Locale.ENGLISH);
        String combined = action + " " + entityType + " " + details;
        if (combined.contains("SALE") || combined.contains("BILL") || combined.contains("INVOICE")) return "BILLING";
        if (combined.contains("PURCHASE") || combined.contains("SUPPLIER")) return "PURCHASE";
        if (combined.contains("STOCK") || combined.contains("BATCH") || combined.contains("PRODUCT") || combined.contains("INVENTORY")) return "INVENTORY";
        if (combined.contains("LOGIN") || combined.contains("LOGOUT") || combined.contains("AUTH") || combined.contains("PASSWORD")) return "AUTH";
        return "SETTINGS";
    }

    private String resolvePriority(AuditLog log) {
        if ("FAILED".equalsIgnoreCase(log.getStatus())) return "High";
        if (actionLooksCritical(log)) return "Attention";
        if ("SUCCESS".equalsIgnoreCase(log.getStatus())) return "Normal";
        return "Tracked";
    }

    private boolean actionLooksCritical(AuditLog log) {
        String combined = safe(log.getAction(), "") + " " + safe(log.getDetails(), "");
        String upper = combined.toUpperCase(Locale.ENGLISH);
        return upper.contains("DELETE") || upper.contains("CANCEL") || upper.contains("SUSPEND") || upper.contains("EXPIRED");
    }

    private boolean isShopOperationalModule(String module) {
        return "BILLING".equalsIgnoreCase(module)
                || "PURCHASE".equalsIgnoreCase(module)
                || "INVENTORY".equalsIgnoreCase(module);
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value != null && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
