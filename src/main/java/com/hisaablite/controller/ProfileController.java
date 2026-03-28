package com.hisaablite.controller;

import com.hisaablite.dto.ShopProfileUpdateRequest;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.entity.PlanType;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.service.ShopService;
import com.hisaablite.service.EvolutionApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
@Slf4j
public class ProfileController {

    private final ShopService shopService;
    private final UserRepository userRepository;
    private final EvolutionApiService evolutionApiService;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public String profilePage(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Shop shop = user.getShop();

        ShopProfileUpdateRequest request = new ShopProfileUpdateRequest();
        request.setGstNumber(shop.getGstNumber());
        request.setAddress(shop.getAddress());
        request.setCity(shop.getCity());
        request.setState(shop.getState());
        request.setPincode(shop.getPincode());
        request.setUpiId(shop.getUpiId());

        model.addAttribute("shop", shop);
        model.addAttribute("profileRequest", request);
        model.addAttribute("role", user.getRole().name());
        
        // Plan Type
        PlanType planType = shop.getPlanType() != null ? shop.getPlanType() : PlanType.FREE;
        String planTypeDisplay = planType.name();
        model.addAttribute("planType", planTypeDisplay);
        model.addAttribute("subscriptionPlan", planTypeDisplay);
        
        // Plan Start Date - Using subscriptionStartDate field
        LocalDateTime planStartDate = shop.getSubscriptionStartDate() != null ? 
                                      shop.getSubscriptionStartDate() : shop.getCreatedAt();
        model.addAttribute("planStartDate", planStartDate);
        
        // Plan End Date & Days Remaining - Using subscriptionEndDate field
        LocalDateTime planEndDate = shop.getSubscriptionEndDate();
        long daysRemaining = 0;
        
        if (planType != PlanType.FREE && planEndDate != null) {
            daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), planEndDate);
            daysRemaining = Math.max(0, daysRemaining);
            model.addAttribute("planEndDate", planEndDate);
            model.addAttribute("daysRemaining", daysRemaining);
        } else {
            model.addAttribute("planEndDate", null);
            model.addAttribute("daysRemaining", null);
        }
        
        // Staff Usage
        long currentStaffCount = userRepository.countByShop(shop);
        int maxStaffLimit = getMaxStaffLimit(planType);
        model.addAttribute("currentStaffCount", currentStaffCount);
        model.addAttribute("maxStaffLimit", maxStaffLimit);
        
        // Products Usage
        long currentProductCount = productRepository.countByShop(shop);
        int productLimitRaw = getProductLimit(planType);
        String productLimit = productLimitRaw == -1 ? "∞" : String.valueOf(productLimitRaw);
        model.addAttribute("currentProductCount", currentProductCount);
        model.addAttribute("productLimitRaw", productLimitRaw);
        model.addAttribute("productLimit", productLimit);
        
        // Invoices Count (for additional info)
        long totalInvoices = saleRepository.countByShop(shop);
        model.addAttribute("totalInvoices", totalInvoices);
        
        model.addAttribute("currentPage", "profile");
        model.addAttribute("user", user);
        
        return "profile";
    }

    @PostMapping
    public String updateProfile(@ModelAttribute ShopProfileUpdateRequest request, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        shopService.updateProfile(user, request);
        return "redirect:/dashboard";
    }
    
    private int getMaxStaffLimit(PlanType planType) {
        switch (planType) {
            case FREE:
                return 1; // Only owner
            case BASIC:
                return 1; // Only owner
            case PREMIUM:
                return 10; // 1 Owner + 9 Staff
            case ENTERPRISE:
                return -1; // Unlimited
            default:
                return 1;
        }
    }
    
    private int getProductLimit(PlanType planType) {
        switch (planType) {
            case FREE:
                return 10;
            case BASIC:
                return 50;
            case PREMIUM:
                return 1000;
            case ENTERPRISE:
                return -1; // Unlimited
            default:
                return 10;
        }
    }

    // ========== WHATSAPP METHODS ==========
    @PostMapping("/whatsapp/create-instance")
    @ResponseBody
    public ResponseEntity<?> createWhatsAppInstance(@RequestParam String whatsappNumber,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
            Shop shop = user.getShop();

            if (whatsappNumber == null || !whatsappNumber.matches("\\d{10}")) {
                response.put("error", "Invalid WhatsApp number");
                return ResponseEntity.badRequest().body(response);
            }

            shop.setWhatsappNumber(whatsappNumber);
            shop = shopService.saveShop(shop);

            String instanceName = "shop_" + shop.getId();

            try {
                evolutionApiService.deleteInstance(instanceName);
            } catch (Exception e) {
                log.info("No existing instance to delete");
            }

            String qrCode = evolutionApiService.createInstance(instanceName);

            if (qrCode == null) {
                qrCode = evolutionApiService.getQRCode(instanceName);
            }

            shop.setWhatsappInstanceName(instanceName);
            shop.setWhatsappQrCode(qrCode);
            shop.setWhatsappConnected(false);
            shop = shopService.saveShop(shop);

            response.put("success", true);
            response.put("qrCode", qrCode);
            response.put("instanceName", instanceName);

            log.info("QR code saved for shop: {}, length: {}", shop.getId(),
                    qrCode != null ? qrCode.length() : 0);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating instance", e);
            response.put("error", "WhatsApp setup failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/whatsapp/check-connection/{instanceName}")
    @ResponseBody
    public ResponseEntity<?> checkInstanceConnection(@PathVariable String instanceName, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean connected = evolutionApiService.checkConnection(instanceName);

            if (connected) {
                User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
                Shop shop = user.getShop();
                shop.setWhatsappConnected(true);
                shop.setWhatsappConnectedAt(LocalDateTime.now());
                shopService.saveShop(shop);
            }

            response.put("connected", connected);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/whatsapp/reset")
    @ResponseBody
    public ResponseEntity<?> resetWhatsApp(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
            Shop shop = user.getShop();

            if (shop.getWhatsappInstanceName() != null) {
                evolutionApiService.deleteInstance(shop.getWhatsappInstanceName());
            }

            shop.setWhatsappInstanceName(null);
            shop.setWhatsappQrCode(null);
            shop.setWhatsappConnected(false);
            shop.setWhatsappConnectedAt(null);
            shopService.saveShop(shop);

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}