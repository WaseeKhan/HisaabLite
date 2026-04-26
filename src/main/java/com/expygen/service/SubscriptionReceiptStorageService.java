package com.expygen.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.Builder;
import lombok.Value;

@Service
public class SubscriptionReceiptStorageService {

    private static final long MAX_RECEIPT_SIZE_BYTES = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp");

    public StoredReceipt store(MultipartFile file, Long requestId) {
        validate(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt");
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String storedFilename = "upgrade-request-" + requestId + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + extension;

        try {
            Path uploadDir = resolveUploadDirectory();
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(storedFilename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return StoredReceipt.builder()
                    .originalFilename(originalFilename)
                    .storedFilename(storedFilename)
                    .contentType(file.getContentType())
                    .uploadedAt(LocalDateTime.now())
                    .build();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store payment receipt.", ex);
        }
    }

    public Resource loadAsResource(String storedFilename) {
        Path file = resolveUploadDirectory().resolve(storedFilename).normalize();
        if (!Files.exists(file)) {
            throw new RuntimeException("Payment receipt file not found.");
        }
        return new PathResource(file);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Payment receipt file is empty.");
        }
        if (file.getSize() > MAX_RECEIPT_SIZE_BYTES) {
            throw new RuntimeException("Payment receipt must be smaller than 8 MB.");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new RuntimeException("Receipt must be a PDF, JPG, PNG, or WEBP file.");
        }
    }

    private Path resolveUploadDirectory() {
        return Paths.get(System.getProperty("user.dir"), "uploads", "subscription-receipts");
    }

    @Value
    @Builder
    public static class StoredReceipt {
        String originalFilename;
        String storedFilename;
        String contentType;
        LocalDateTime uploadedAt;
    }
}
