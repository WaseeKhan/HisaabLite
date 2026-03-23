package com.hisaablite.controller;

import com.hisaablite.dto.ShopProfileUpdateRequest;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.entity.PlanType;
import com.hisaablite.repository.UserRepository;
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
        model.addAttribute("currentPage", "products");

        PlanType planType = shop.getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);
        model.addAttribute("subscriptionPlan", planTypeDisplay);

        return "profile";
    }

    @PostMapping
    public String updateProfile(@ModelAttribute ShopProfileUpdateRequest request, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        shopService.updateProfile(user, request);
        return "redirect:/dashboard";
    }

    // ========== STEP 1: CREATE INSTANCE ==========
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

            // Delete if exists
            try {
                evolutionApiService.deleteInstance(instanceName);
            } catch (Exception e) {
                log.info("No existing instance to delete");
            }

            // Create new instance
            String qrCode = evolutionApiService.createInstance(instanceName);

            // Check if QR code is null (instance created but no QR)
            if (qrCode == null) {
                // Try to fetch QR separately
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

    // ========== STEP 2: CHECK CONNECTION ==========
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
                shopService.saveShop(shop);
            }

            response.put("connected", connected);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ========== STEP 3: RESET / DISCONNECT ==========
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
            shopService.saveShop(shop);

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}