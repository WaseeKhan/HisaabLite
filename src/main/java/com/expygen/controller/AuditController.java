package com.expygen.controller;

import com.expygen.admin.repository.AuditLogRepository;
import com.expygen.entity.AuditLog;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/activity")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String shopAuditPage(Model model,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Shop shop = user.getShop();

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
            Page<AuditLog> auditPage = auditLogRepository.searchShopAuditLogs(shop.getId(), username, action, status, pageable);

            model.addAttribute("shop", shop);
            model.addAttribute("user", user);
            model.addAttribute("role", user.getRole().name());
            model.addAttribute("planType", shop.getPlanType() != null ? shop.getPlanType().name() : "FREE");
            model.addAttribute("currentPage", "activity");

            model.addAttribute("auditLogs", auditPage);
            model.addAttribute("currentPageNumber", auditPage.getNumber());
            model.addAttribute("totalPages", auditPage.getTotalPages());
            model.addAttribute("pageSize", size);
            model.addAttribute("username", username);
            model.addAttribute("selectedAction", action);
            model.addAttribute("selectedStatus", status);

            model.addAttribute("availableActions", auditLogRepository.findDistinctActionsByShopId(shop.getId()));
            model.addAttribute("availableUsers", auditLogRepository.findDistinctUsernamesByShopId(shop.getId()));
            model.addAttribute("totalAuditLogs", auditLogRepository.countByShopId(shop.getId()));
            model.addAttribute("failedAuditLogs", auditLogRepository.countFailedActionsByShopId(shop.getId()));
            model.addAttribute("recentAuditLogs",
                    auditLogRepository.countRecentActionsByShopId(shop.getId(), LocalDateTime.now().minusHours(24)));
            model.addAttribute("actionSummary",
                    auditLogRepository.getShopActionStats(shop.getId(), LocalDateTime.now().minusDays(7)));
            model.addAttribute("userActivitySummary",
                    auditLogRepository.getShopUserActivitySummary(shop.getId(), PageRequest.of(0, 5)));
        } catch (Exception e) {
            log.error("Error loading shop audit page", e);
            model.addAttribute("shop", shop);
            model.addAttribute("user", user);
            model.addAttribute("role", user.getRole().name());
            model.addAttribute("planType", shop.getPlanType() != null ? shop.getPlanType().name() : "FREE");
            model.addAttribute("currentPage", "activity");
            model.addAttribute("auditLogs", Page.empty());
            model.addAttribute("currentPageNumber", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("pageSize", size);
            model.addAttribute("username", username);
            model.addAttribute("selectedAction", action);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("availableActions", List.of());
            model.addAttribute("availableUsers", List.of());
            model.addAttribute("totalAuditLogs", 0L);
            model.addAttribute("failedAuditLogs", 0L);
            model.addAttribute("recentAuditLogs", 0L);
            model.addAttribute("actionSummary", List.of());
            model.addAttribute("userActivitySummary", List.of());
            model.addAttribute("error", "Could not load activity history right now.");
        }

        return "activity-history";
    }
}
