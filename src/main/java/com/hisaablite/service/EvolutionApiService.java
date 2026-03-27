package com.hisaablite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EvolutionApiService {

    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;

    @Value("${evolution.api.key:hisaablite-local-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Directly fetch QR code from Evolution API using connect endpoint
     */
    public String fetchQRDirectly(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/connect/" + instanceName;
            log.info("Directly fetching QR for instance: {}", instanceName);

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);

            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode json = objectMapper.readTree(response.getBody());
                log.info("Direct QR response: {}", json.toString());

                // Try all possible paths
                if (json.has("qrcode")) {
                    JsonNode qrNode = json.get("qrcode");
                    if (qrNode.has("code")) {
                        return qrNode.get("code").asText();
                    } else if (qrNode.isTextual()) {
                        return qrNode.asText();
                    }
                }

                if (json.has("base64")) {
                    return json.get("base64").asText();
                }

                if (json.has("image")) {
                    return json.get("image").asText();
                }
            }
        } catch (Exception e) {
            log.error("Error directly fetching QR", e);
        }
        return null;
    }

    /**
     * Create WhatsApp instance
     */
    public String createInstance(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/create";
            log.info("Creating instance: {}", instanceName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("instanceName", instanceName);
            body.put("integration", "WHATSAPP-BAILEYS");
            body.put("qrcode", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {

                JsonNode json = objectMapper.readTree(response.getBody());

                String qrCode = null;

                // SAFE QR EXTRACTION
                if (json.has("qrcode") && json.get("qrcode").has("code")) {
                    qrCode = json.get("qrcode").get("code").asText();
                }
                else if (json.has("qrcode") && json.get("qrcode").has("base64")) {
                    qrCode = json.get("qrcode").get("base64").asText();
                }
                else if (json.has("base64")) {
                    qrCode = json.get("base64").asText();
                }
                else if (json.has("instance") && json.get("instance").has("qrcode")) {

                    JsonNode qrNode = json.get("instance").get("qrcode");

                    if (qrNode.has("code")) {
                        qrCode = qrNode.get("code").asText();
                    }
                    else if (qrNode.has("base64")) {
                        qrCode = qrNode.get("base64").asText();
                    }
                }

                if (qrCode == null || qrCode.isEmpty()) {
                    log.warn("QR not found in create response");
                    return null;
                }

                // remove prefix if present
                if (qrCode.startsWith("data:image")) {
                    qrCode = qrCode.substring(qrCode.indexOf(",") + 1);
                }

                log.info("QR generated successfully. Length: {}", qrCode.length());

                return qrCode;
            }

            throw new RuntimeException("Instance creation failed");

        } catch (Exception e) {
            log.error("Error creating instance", e);
            throw new RuntimeException("WhatsApp setup failed", e);
        }
    }

    /**
     * Check instance connection status
     */
    public boolean checkConnection(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/connectionState/" + instanceName;

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            JsonNode json = objectMapper.readTree(response.getBody());

            log.info("Connection API response: {}", json);
            log.info("Connection API response: {}", response.getBody());

            String state = null;

            if (json.has("instance") && json.get("instance").has("state")) {
                state = json.get("instance").get("state").asText();
            }
            else if (json.has("state")) {
                state = json.get("state").asText();
            }
            else if (json.has("connectionStatus")) {
                state = json.get("connectionStatus").asText();
            }

            return "open".equalsIgnoreCase(state);

        } catch (Exception e) {
            log.error("Error checking connection", e);
            return false;
        }
    }

    /**
     * Get QR code for instance
     */
    public String getQRCode(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/connect/" + instanceName;
            log.info("Fetching QR code for instance: {}", instanceName);

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);

            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode json = objectMapper.readTree(response.getBody());
                String qrCode = json.path("qrcode").path("code").asText();

                if (qrCode != null && !qrCode.isEmpty()) {
                    log.info("QR code fetched successfully, length: {}", qrCode.length());
                    return qrCode;
                }
            }

        } catch (Exception e) {
            log.error("Error fetching QR code for {}: {}", instanceName, e.getMessage());
        }
        return null;
    }

    /**
     * Delete WhatsApp instance
     */
    public boolean deleteInstance(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/delete/" + instanceName;
            log.info("Deleting Evolution API instance: {}", instanceName);

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);

            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Instance deleted successfully: {}", instanceName);
                return true;
            } else {
                log.warn("Failed to delete instance: {}, status: {}", instanceName, response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error deleting instance: {}", instanceName, e);
            return false;
        }
    }

    /**
     * Check if instance exists
     */
    public boolean instanceExists(String instanceName) {
        try {
            String url = evolutionApiUrl + "/instance/fetchInstances";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);
            HttpEntity<?> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
                    
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode json = objectMapper.readTree(response.getBody());
                if (json.isArray()) {
                    for (JsonNode instance : json) {
                        if (instance.has("instanceName") && 
                            instance.get("instanceName").asText().equals(instanceName)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking instance existence", e);
            return false;
        }
    }

    /**
     * Format phone number for WhatsApp
     */
    private String formatPhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() == 10) {
            cleaned = "91" + cleaned;
        }
        return cleaned;
    }

/**
     * Send media message (document, image, etc.) via WhatsApp with specific instance
     */
    public boolean sendMediaMessage(String instanceName, String phoneNumber, String caption, String fileName, byte[] fileData, String mediaType) {
        try {
            if (instanceName == null || instanceName.isEmpty()) {
                log.error("Instance name is null or empty");
                return false;
            }
            
            String url = evolutionApiUrl + "/message/sendMedia/" + instanceName;
            String formattedPhone = formatPhoneNumber(phoneNumber);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("number", formattedPhone);
            requestBody.put("mediatype", mediaType);
            requestBody.put("fileName", fileName);
            requestBody.put("caption", caption);
            requestBody.put("media", Base64.getEncoder().encodeToString(fileData));
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            log.info("Sending media message to: {} using instance: {}", formattedPhone, instanceName);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            log.info("Media message response status: {}", response.getStatusCode());
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Error sending media message", e);
            return false;
        }
    }
}