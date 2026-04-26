package com.expygen.controller;

import com.expygen.dto.ShopProfileUpdateRequest;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.service.ShopService;
import com.expygen.service.EvolutionApiService;
import com.expygen.service.PlanLimitService;
import com.expygen.service.ShopLogoStorageService;
import com.expygen.service.ShopSealStorageService;
import com.expygen.service.SubscriptionAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final PlanLimitService planLimitService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final ShopLogoStorageService shopLogoStorageService;
    private final ShopSealStorageService shopSealStorageService;

    @GetMapping
    @Transactional(readOnly = true)
    public String profilePage(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Shop shop = user.getShop();

        ShopProfileUpdateRequest request = new ShopProfileUpdateRequest();
        request.setShopName(shop.getName());
        request.setGstNumber(shop.getGstNumber());
        request.setAddress(shop.getAddress());
        request.setCity(shop.getCity());
        request.setState(shop.getState());
        request.setPincode(shop.getPincode());
        request.setUpiId(shop.getUpiId());

        model.addAttribute("shop", shop);
        model.addAttribute("profileRequest", request);
        model.addAttribute("role", user.getRole().name());
        
        String planTypeDisplay = subscriptionAccessService.getPlanName(shop);
        model.addAttribute("planType", planTypeDisplay);
        model.addAttribute("subscriptionPlan", planTypeDisplay);
        
        // Plan Start Date - Using subscriptionStartDate field
        LocalDateTime planStartDate = shop.getSubscriptionStartDate() != null ? 
                                      shop.getSubscriptionStartDate() : shop.getCreatedAt();
        model.addAttribute("planStartDate", planStartDate);
        
        // Plan End Date & Days Remaining - Using subscriptionEndDate field
        LocalDateTime planEndDate = shop.getSubscriptionEndDate();
        long daysRemaining = 0;
        
        if (!subscriptionAccessService.isFreePlan(shop) && planEndDate != null) {
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
        int maxStaffLimit = planLimitService.getUserLimit(shop);
        model.addAttribute("currentStaffCount", currentStaffCount);
        model.addAttribute("maxStaffLimit", maxStaffLimit);
        model.addAttribute("staffLimitUnlimited", maxStaffLimit == -1);

        // Products Usage
        long currentProductCount = productRepository.countByShopAndActiveTrue(shop);
        int productLimitRaw = planLimitService.getProductLimit(shop);
        String productLimit = productLimitRaw == -1 ? "∞" : String.valueOf(productLimitRaw);
        model.addAttribute("currentProductCount", currentProductCount);
        model.addAttribute("productLimitRaw", productLimitRaw);
        model.addAttribute("productLimit", productLimit);
        model.addAttribute("productLimitUnlimited", productLimitRaw == -1);
        model.addAttribute("whatsappAvailable", subscriptionAccessService.canUseWhatsAppIntegration(shop));
        model.addAttribute("whatsappUpgradeMessage", subscriptionAccessService.getWhatsAppUpgradeMessage(shop));
        model.addAttribute("hasShopLogo", shop.getLogoStoredFilename() != null && !shop.getLogoStoredFilename().isBlank());
        model.addAttribute("hasShopSeal", shop.getSealStoredFilename() != null && !shop.getSealStoredFilename().isBlank());
        
        // Invoices Count (for additional info)
        long totalInvoices = saleRepository.countByShop(shop);
        model.addAttribute("totalInvoices", totalInvoices);
        
        model.addAttribute("currentPage", "profile");
        model.addAttribute("user", user);
        
        return "profile";
    }

    @PostMapping
    public String updateProfile(@ModelAttribute ShopProfileUpdateRequest request,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        shopService.updateProfile(user, request);
        redirectAttributes.addFlashAttribute("success", "Shop profile updated successfully.");
        return "redirect:/profile";
    }

    @PostMapping("/logo")
    public String uploadShopLogo(@RequestParam("logoFile") MultipartFile logoFile,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
            shopLogoStorageService.storeForShop(user.getShop(), logoFile);
            redirectAttributes.addFlashAttribute("success", "Shop logo updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/logo/remove")
    public String removeShopLogo(Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        shopLogoStorageService.removeForShop(user.getShop());
        redirectAttributes.addFlashAttribute("success", "Shop logo removed.");
        return "redirect:/profile";
    }

    @GetMapping("/logo")
    @ResponseBody
    public ResponseEntity<Resource> getShopLogo(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Shop shop = user.getShop();

        if (shop.getLogoStoredFilename() == null || shop.getLogoStoredFilename().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = shopLogoStorageService.loadForShop(shop);
        MediaType mediaType = shop.getLogoContentType() != null
                ? MediaType.parseMediaType(shop.getLogoContentType())
                : MediaType.IMAGE_PNG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(resource);
    }

    @PostMapping("/seal")
    public String uploadShopSeal(@RequestParam("sealFile") MultipartFile sealFile,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
            shopSealStorageService.storeForShop(user.getShop(), sealFile);
            redirectAttributes.addFlashAttribute("success", "Invoice seal updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/seal/remove")
    public String removeShopSeal(Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        shopSealStorageService.removeForShop(user.getShop());
        redirectAttributes.addFlashAttribute("success", "Invoice seal removed.");
        return "redirect:/profile";
    }

    @GetMapping("/seal")
    @ResponseBody
    public ResponseEntity<Resource> getShopSeal(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Shop shop = user.getShop();

        if (shop.getSealStoredFilename() == null || shop.getSealStoredFilename().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = shopSealStorageService.loadForShop(shop);
        MediaType mediaType = shop.getSealContentType() != null
                ? MediaType.parseMediaType(shop.getSealContentType())
                : MediaType.IMAGE_PNG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(resource);
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

            if (!subscriptionAccessService.canUseWhatsAppIntegration(shop)) {
                response.put("error", subscriptionAccessService.getWhatsAppUpgradeMessage(shop));
                return ResponseEntity.status(403).body(response);
            }

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

            if (!subscriptionAccessService.canUseWhatsAppIntegration(shop)) {
                response.put("error", subscriptionAccessService.getWhatsAppUpgradeMessage(shop));
                return ResponseEntity.status(403).body(response);
            }

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
