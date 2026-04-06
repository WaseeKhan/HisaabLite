package com.hisaablite.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.hisaablite.config.AppConfig;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.Shop;
import com.hisaablite.repository.SaleItemRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsAppService {

    private final SaleItemRepository saleItemRepository;
    private final PdfService pdfService;
    private final AppConfig appConfig;
    private final EvolutionApiService evolutionApiService; // Added this dependency
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;

    @Value("${evolution.api.key:hisaablite-local-key}")
    private String apiKey;

    /**
     * Get base URL from current request
     */
    private String getAppBaseUrl() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(
                    RequestContextHolder.getRequestAttributes())).getRequest();

            String appUrl = request.getRequestURL().toString()
                    .replace(request.getServletPath(), "");

            log.debug("App URL generated: {}", appUrl);
            return appUrl;

        } catch (Exception e) {
            log.error("Could not get request URL", e);
            throw new RuntimeException("Could not generate app URL", e);
        }
    }

    /**
     * Send invoice on WhatsApp (Text only)
     */
    public boolean sendInvoice(Sale sale, String customerPhone) {
        try {
            String formattedPhone = formatPhoneNumber(customerPhone);
            String message = buildInvoiceMessage(sale);
            String instanceName = sale.getShop().getWhatsappInstanceName();

            if (instanceName == null || !sale.getShop().isWhatsappConnected()) {
                log.warn("Shop {} WhatsApp not connected", sale.getShop().getId());
                return false;
            }
            return sendMessage(instanceName, formattedPhone, message);
        } catch (Exception e) {
            log.error("Failed to send invoice: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send invoice as generated PDF attachment using shop's WhatsApp instance
     */
    public boolean sendInvoiceWithPdf(Sale sale, String customerPhone) {
        log.info("========== START sendInvoiceWithPdf ==========");
        log.info("Sale ID: {}, Customer Phone: {}", sale.getId(), customerPhone);

        Shop shop = sale.getShop();
        if (shop == null) {
            log.error("Shop not found for sale: {}", sale.getId());
            return false;
        }

        String instanceName = shop.getWhatsappInstanceName();
        if (instanceName == null || instanceName.isEmpty()) {
            log.error("WhatsApp instance not configured for shop: {}. Expected format: shop_{}",
                    shop.getId(), shop.getId());
            return false;
        }

        if (!shop.isWhatsappConnected()) {
            log.warn("Shop {} WhatsApp not connected. Instance: {}", shop.getId(), instanceName);
            return false;
        }

        try {
            String formattedPhone = formatPhoneNumber(customerPhone);
            String fileName = "invoice_" + sale.getId() + ".pdf";
            String caption = String.format(
                    "*" + appConfig.getAppName() + " Invoice*\n\n" +
                            "Thank you for your purchase!\n\n" +
                            "📄 *Invoice #%d*\n" +
                            "🏪 *Shop:* %s\n" +
                            "👤 *Customer:* %s\n" +
                            "💰 *Total:* ₹%.2f\n\n" +
                            "Please find your invoice attached.\n\n" +
                            "_This is a system generated invoice._",
                    sale.getId(),
                    shop.getName(),
                    sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in Customer",
                    sale.getTotalAmount());

            log.info("Generating PDF attachment...");
            byte[] pdfBytes = pdfService.generateInvoicePdf(sale);
            log.info("PDF generated, size: {} bytes", pdfBytes.length);

            boolean sent = evolutionApiService.sendMediaMessage(
                    instanceName,
                    formattedPhone,
                    caption,
                    fileName,
                    pdfBytes,
                    "document");

            log.info("Message sent using instance {}: {}", instanceName, sent);
            log.info("========== END sendInvoiceWithPdf ==========");

            return sent;

        } catch (Exception e) {
            log.error("Failed to send message for shop {}: {}",
                    shop.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send invoice as PDF attachment via WhatsApp
     */
    public boolean sendInvoiceWithPdfAttachment(Sale sale, String phoneNumber, MultipartFile pdfFile) {
        try {
            log.info("Sending invoice PDF as attachment to {}", phoneNumber);

            Shop shop = sale.getShop();

            // Get the WhatsApp instance name from the shop (format: shop_<SHOPID>)
            String instanceName = shop.getWhatsappInstanceName();

            if (instanceName == null || instanceName.isEmpty()) {
                log.error("WhatsApp instance not configured for shop: {}. Expected format: shop_{}",
                        shop.getId(), shop.getId());
                return false;
            }

            if (!shop.isWhatsappConnected()) {
                log.warn("Shop {} WhatsApp not connected. Instance: {}", shop.getId(), instanceName);
                return false;
            }

            String message = String.format(
                    "*" + appConfig.getAppName() + " Invoice*\n\n" +
                            "Thank you for your purchase!\n\n" +
                            "📄 *Invoice #%d*\n" +
                            "🏪 *Shop:* %s\n" +
                            "👤 *Customer:* %s\n" +
                            "💰 *Total:* ₹%.2f\n\n" +
                            "Please find your invoice attached.\n\n" +
                            "_This is a system generated invoice._",
                    sale.getId(),
                    shop.getName(),
                    sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in Customer",
                    sale.getTotalAmount());

            log.info("Using instance name: {} for shop ID: {}", instanceName, shop.getId());

            // Pass the instance name to the sendMediaMessage method
            boolean sent = evolutionApiService.sendMediaMessage(
                    instanceName, // This will be like "shop_123"
                    phoneNumber,
                    message,
                    pdfFile.getOriginalFilename(),
                    pdfFile.getBytes(),
                    "document");

            String maskedPhone = phoneNumber != null && phoneNumber.length() >= 4
                    ? "xxxxxx" + phoneNumber.substring(phoneNumber.length() - 4)
                    : "xxxxxx";

            log.info("Invoice PDF sent successfully to {} using instance: {}", maskedPhone, instanceName);

            // log.info("Invoice PDF sent successfully to {} using instance: {}",
            // phoneNumber, instanceName);

            if (sent) {
                log.info("Invoice PDF sent successfully to {} using instance: {}", maskedPhone, instanceName);
                return true;
            } else {
                log.error("Failed to send invoice PDF to {} using instance: {}", maskedPhone, instanceName);
                return false;
            }

        } catch (Exception e) {
            log.error("Error sending invoice PDF attachment", e);
            return false;
        }
    }

    /**
     * Send PDF document via Evolution API (File upload)
     */
    private boolean sendPdfDocument(String instanceName, String phoneNumber, File pdfFile, String fileName) {
        try {
            String url = evolutionApiUrl + "/message/sendMedia/" + instanceName;
            log.info("Sending PDF to URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("apikey", apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("number", phoneNumber);
            body.add("mediatype", "document");
            body.add("fileName", fileName);

            FileSystemResource fileResource = new FileSystemResource(pdfFile);
            body.add("document", fileResource);
            body.add("caption", appConfig.getAppName() + " Invoice #" +
                    pdfFile.getName().replaceAll("[^0-9]", ""));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            log.info("PDF send response status: {}", response.getStatusCode());
            return response.getStatusCode() == HttpStatus.CREATED;

        } catch (Exception e) {
            log.error("Failed to send PDF - Exception: ", e);
            return false;
        }
    }

    /**
     * Send PDF as Base64 (More reliable than file upload)
     */
    private boolean sendPdfAsBase64(String instanceName, String phoneNumber, byte[] pdfBytes, String fileName) {
        try {
            String url = evolutionApiUrl + "/message/sendMedia/" + instanceName;
            log.info("Sending PDF as Base64 to: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apiKey);

            String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);

            Map<String, Object> body = new HashMap<>();
            body.put("number", phoneNumber);
            body.put("mediatype", "document");
            body.put("fileName", fileName);
            body.put("document", base64Pdf);
            body.put("caption", appConfig.getAppName() + " Invoice");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.info("Base64 response status: {}", response.getStatusCode());

            return response.getStatusCode() == HttpStatus.CREATED;

        } catch (Exception e) {
            log.error("Base64 send failed", e);
            return false;
        }
    }

    /**
     * Send text message via Evolution API
     */
    private boolean sendMessage(String instanceName, String phoneNumber, String message) {
        try {
            String url = evolutionApiUrl + "/message/sendText/" + instanceName;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("number", phoneNumber);
            body.put("text", message);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Text message sent to: {}", phoneNumber);
                return true;
            }
            log.warn("Text message send failed with status: {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            log.error("Send failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Format phone number for WhatsApp (India)
     */
    private String formatPhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() == 10) {
            cleaned = "91" + cleaned;
        }
        return cleaned;
    }

    /**
     * Build invoice message with PDF link
     */
    private String buildInvoiceMessage(Sale sale) {
        String appUrl = getAppBaseUrl();
        String pdfLink = appUrl + "/sales/invoice/" + sale.getId() + "/pdf";
        String customerName = sale.getCustomerName() != null ? sale.getCustomerName() : "Customer";
        String shopName = sale.getShop().getName();

        StringBuilder message = new StringBuilder();
        message.append("Hello *").append(customerName).append("*, 👋 \n\n");
        message.append("🛍️ Thank you for visiting *").append(shopName).append("*.\n\n");
        message.append("📎 *Download Invoice*\n");
        message.append(pdfLink).append("\n\n");
        message.append("🌟 We hope to see you again soon.\n\n");
        message.append("Best Wishes,\n");
        message.append("*").append(shopName).append(" Team* 🤝 ");

        return message.toString();
    }

    /**
     * Check connection status
     */
    public boolean isConnected(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/connectionState/" + instanceName;

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);

            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("instance")) {
                Map<String, Object> instance = (Map<String, Object>) response.getBody().get("instance");
                String state = (String) instance.get("state");
                return "open".equals(state);
            }
        } catch (Exception e) {
            log.error("Connection check failed: {}", e.getMessage());
        }
        return false;
    }
}
