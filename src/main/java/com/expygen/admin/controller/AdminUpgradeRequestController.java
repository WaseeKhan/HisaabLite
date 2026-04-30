package com.expygen.admin.controller;

import com.expygen.admin.repository.AdminShopRepository;
import com.expygen.dto.SubscriptionLifecycleSnapshot;
import com.expygen.entity.PlanType;
import com.expygen.entity.Shop;
import com.expygen.entity.UpgradeRequest;
import com.expygen.entity.UpgradeRequestStatus;
import com.expygen.entity.User;
import com.expygen.repository.UpgradeRequestRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.SubscriptionLifecycleService;
import com.expygen.service.SubscriptionLifecycleStatus;
import com.expygen.service.SubscriptionReceiptStorageService;
import com.expygen.service.UpgradeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/upgrade-requests")
@RequiredArgsConstructor
public class AdminUpgradeRequestController {

    private final UpgradeRequestService upgradeRequestService;
    private final UserRepository userRepository;
    private final SubscriptionReceiptStorageService subscriptionReceiptStorageService;
    private final AdminShopRepository adminShopRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final UpgradeRequestRepository upgradeRequestRepository;

    private static final List<UpgradeRequestStatus> OPEN_COMMERCIAL_STATUSES = List.of(
            UpgradeRequestStatus.REQUESTED,
            UpgradeRequestStatus.CONTACTED,
            UpgradeRequestStatus.PAYMENT_RECEIVED);

    @GetMapping
    public String requests(@RequestParam(required = false) UpgradeRequestStatus status, Model model) {
        Page<UpgradeRequest> requestPage = upgradeRequestService.getRequests(status, PageRequest.of(0, 50));
        List<UpgradeRequest> requests = requestPage.getContent();
        List<Shop> allShops = adminShopRepository.findAll();
        List<CommercialShopRow> shopRows = buildCommercialShopRows(allShops);
        Map<UpgradeRequestStatus, Long> statusCounts = upgradeRequestRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(
                        row -> (UpgradeRequestStatus) row[0],
                        row -> (Long) row[1]));

        model.addAttribute("requests", requests);
        model.addAttribute("selectedStatus", status != null ? status.name() : "ALL");
        model.addAttribute("statuses", UpgradeRequestStatus.values());
        model.addAttribute("totalRequestCount", requestPage.getTotalElements());
        model.addAttribute("requestedCount", statusCounts.getOrDefault(UpgradeRequestStatus.REQUESTED, 0L));
        model.addAttribute("contactedCount", statusCounts.getOrDefault(UpgradeRequestStatus.CONTACTED, 0L));
        model.addAttribute("paymentReceivedCount", statusCounts.getOrDefault(UpgradeRequestStatus.PAYMENT_RECEIVED, 0L));
        model.addAttribute("activatedCount", statusCounts.getOrDefault(UpgradeRequestStatus.ACTIVATED, 0L));
        model.addAttribute("pendingActivationRequests", requests.stream()
                .filter(request -> request.getStatus() == UpgradeRequestStatus.PAYMENT_RECEIVED)
                .toList());
        model.addAttribute("followUpRequests", requests.stream()
                .filter(request -> request.getStatus() == UpgradeRequestStatus.REQUESTED
                        || request.getStatus() == UpgradeRequestStatus.CONTACTED)
                .toList());
        model.addAttribute("paidShops", filterShopRows(shopRows, row -> row.shop().getPlanType() != null
                && row.shop().getPlanType() != PlanType.FREE));
        model.addAttribute("renewalDueShops", filterShopRows(shopRows,
                row -> row.lifecycle().getStatus() == SubscriptionLifecycleStatus.RENEWAL_DUE));
        model.addAttribute("graceOrExpiredShops", filterShopRows(shopRows,
                row -> row.lifecycle().getStatus() == SubscriptionLifecycleStatus.GRACE_PERIOD
                        || row.lifecycle().getStatus() == SubscriptionLifecycleStatus.EXPIRED));
        return "admin/upgrade-requests";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam UpgradeRequestStatus status,
                               @RequestParam(required = false) String adminNote,
                               @RequestParam(required = false) String paymentReference,
                               Authentication authentication) {
        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        upgradeRequestService.updateStatus(id, status, adminNote, paymentReference, admin);
        return "redirect:/admin/upgrade-requests";
    }

    @GetMapping("/{id}/receipt")
    @ResponseBody
    public ResponseEntity<Resource> downloadReceipt(@PathVariable Long id) {
        var request = upgradeRequestService.getRequest(id);
        if (request.getReceiptStoredFilename() == null || request.getReceiptStoredFilename().isBlank()) {
            throw new RuntimeException("Receipt not uploaded for this request.");
        }

        Resource resource = subscriptionReceiptStorageService.loadAsResource(request.getReceiptStoredFilename());
        MediaType mediaType = request.getReceiptContentType() != null && !request.getReceiptContentType().isBlank()
                ? MediaType.parseMediaType(request.getReceiptContentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (request.getReceiptOriginalFilename() != null
                                ? request.getReceiptOriginalFilename()
                                : request.getReceiptStoredFilename()) + "\"")
                .body(resource);
    }

    private List<CommercialShopRow> buildCommercialShopRows(List<Shop> shops) {
        if (shops == null || shops.isEmpty()) {
            return List.of();
        }

        Map<Long, UpgradeRequest> openRequestsByShopId = upgradeRequestRepository
                .findByShopInAndStatusInOrderByCreatedAtDesc(shops, OPEN_COMMERCIAL_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        request -> request.getShop().getId(),
                        request -> request,
                        (existing, replacement) -> existing));

        return shops.stream()
                .map(shop -> new CommercialShopRow(
                        shop,
                        subscriptionLifecycleService.buildSnapshot(shop),
                        openRequestsByShopId.get(shop.getId())))
                .sorted(Comparator
                        .comparing((CommercialShopRow row) -> row.shop().getSubscriptionEndDate(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(row -> row.shop().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<CommercialShopRow> filterShopRows(List<CommercialShopRow> rows, Predicate<CommercialShopRow> predicate) {
        return rows.stream()
                .filter(predicate)
                .limit(8)
                .toList();
    }

    private record CommercialShopRow(Shop shop,
                                     SubscriptionLifecycleSnapshot lifecycle,
                                     UpgradeRequest openRequest) {
    }
}
