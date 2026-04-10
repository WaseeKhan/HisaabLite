package com.expygen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EvolutionApiService {
    private static final int MAX_LOG_BODY_LENGTH = 400;

    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;

    @Value("${evolution.api.key:expygen-local-key}")
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
            log.info("Creating Evolution instance '{}' via {} with api key {}", instanceName, url, maskApiKey());

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

            if (response.getStatusCode().is2xxSuccessful()) {
                String qrCode = extractQrCode(response.getBody());

                if (qrCode == null || qrCode.isBlank()) {
                    log.warn("QR not present in create response for '{}'; trying direct QR fetch", instanceName);
                    qrCode = fetchQRDirectly(instanceName);
                }

                if (qrCode == null || qrCode.isBlank()) {
                    log.warn("Direct QR fetch was empty for '{}'; trying fallback QR endpoint", instanceName);
                    qrCode = getQRCode(instanceName);
                }

                if (qrCode == null || qrCode.isBlank()) {
                    log.warn("QR not found after instance creation flow for '{}'", instanceName);
                    return null;
                }

                log.info("QR generated successfully for '{}'. Length: {}", instanceName, qrCode.length());
                return qrCode;
            }

            throw new RuntimeException("Instance creation failed");

        } catch (HttpStatusCodeException e) {
            log.error("Evolution instance create failed for '{}' with status {} and body {}",
                    instanceName,
                    e.getStatusCode(),
                    summarizeResponseBody(e.getResponseBodyAsString()));
            if (e.getStatusCode() == HttpStatus.FORBIDDEN
                    && e.getResponseBodyAsString() != null
                    && e.getResponseBodyAsString().toLowerCase().contains("already in use")) {
                log.warn("Instance '{}' already exists; trying to fetch QR instead of failing immediately", instanceName);
                String qrCode = fetchQRDirectly(instanceName);
                if (qrCode == null || qrCode.isBlank()) {
                    qrCode = getQRCode(instanceName);
                }
                if (qrCode != null && !qrCode.isBlank()) {
                    return qrCode;
                }
            }
            throw new RuntimeException("WhatsApp setup failed with Evolution API status " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Error creating Evolution instance '{}' via {}", instanceName, evolutionApiUrl, e);
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
            } else if (json.has("state")) {
                state = json.get("state").asText();
            } else if (json.has("connectionStatus")) {
                state = json.get("connectionStatus").asText();
            } else if (json.has("instance") && json.get("instance").has("connectionStatus")) {
                state = json.get("instance").get("connectionStatus").asText();
            } else if (json.has("status")) {
                state = json.get("status").asText();
            }

            return isConnectedState(state);

        } catch (HttpStatusCodeException e) {
            log.error("Error checking Evolution connection for '{}' with status {} and body {}",
                    instanceName,
                    e.getStatusCode(),
                    summarizeResponseBody(e.getResponseBodyAsString()));
            return false;
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
                String qrCode = extractQrCode(response.getBody());

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
            log.info("Deleting Evolution API instance '{}' via {}", instanceName, url);

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

        } catch (HttpStatusCodeException e) {
            log.error("Error deleting Evolution instance '{}' with status {} and body {}",
                    instanceName,
                    e.getStatusCode(),
                    summarizeResponseBody(e.getResponseBodyAsString()));
            return false;
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
            
        } catch (HttpStatusCodeException e) {
            log.error("Evolution media message failed for instance '{}' with status {} and body {}",
                    instanceName,
                    e.getStatusCode(),
                    summarizeResponseBody(e.getResponseBodyAsString()));
            return false;
        } catch (Exception e) {
            log.error("Error sending media message", e);
            return false;
        }
    }

    private String maskApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "<missing>";
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return apiKey.substring(0, 2) + "****" + apiKey.substring(apiKey.length() - 2);
    }

    private String summarizeResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_LOG_BODY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOG_BODY_LENGTH) + "...";
    }

    private boolean isConnectedState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        return "open".equalsIgnoreCase(state)
                || "connected".equalsIgnoreCase(state)
                || "online".equalsIgnoreCase(state);
    }

    private String extractQrCode(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        return extractQrCode(objectMapper.readTree(responseBody));
    }

    private String extractQrCode(JsonNode json) {
        if (json == null || json.isNull() || json.isMissingNode()) {
            return null;
        }

        String qrCode = null;

        if (json.has("qrcode")) {
            JsonNode qrNode = json.get("qrcode");
            if (qrNode.isTextual()) {
                qrCode = qrNode.asText();
            } else if (qrNode.has("code")) {
                qrCode = qrNode.get("code").asText();
            } else if (qrNode.has("base64")) {
                qrCode = qrNode.get("base64").asText();
            }
        }

        if ((qrCode == null || qrCode.isBlank()) && json.has("base64")) {
            qrCode = json.get("base64").asText();
        }

        if ((qrCode == null || qrCode.isBlank()) && json.has("code")) {
            qrCode = json.get("code").asText();
        }

        if ((qrCode == null || qrCode.isBlank()) && json.has("image")) {
            qrCode = json.get("image").asText();
        }

        if ((qrCode == null || qrCode.isBlank()) && json.has("instance")) {
            qrCode = extractQrCode(json.get("instance"));
        }

        if (qrCode != null && qrCode.startsWith("data:image")) {
            qrCode = qrCode.substring(qrCode.indexOf(',') + 1);
        }

        return qrCode;
    }
}
