package com.expygen.admin.controller;

import com.expygen.entity.UpgradeRequestStatus;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.SubscriptionReceiptStorageService;
import com.expygen.service.UpgradeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/upgrade-requests")
@RequiredArgsConstructor
public class AdminUpgradeRequestController {

    private final UpgradeRequestService upgradeRequestService;
    private final UserRepository userRepository;
    private final SubscriptionReceiptStorageService subscriptionReceiptStorageService;

    @GetMapping
    public String requests(@RequestParam(required = false) UpgradeRequestStatus status, Model model) {
        model.addAttribute("requests", upgradeRequestService.getRequests(status, PageRequest.of(0, 50)).getContent());
        model.addAttribute("selectedStatus", status != null ? status.name() : "ALL");
        model.addAttribute("statuses", UpgradeRequestStatus.values());
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
}
