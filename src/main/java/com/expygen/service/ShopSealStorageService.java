package com.expygen.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.expygen.entity.Shop;
import com.expygen.repository.ShopRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShopSealStorageService {

    private static final long MAX_FILE_SIZE = 3L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    private final ShopRepository shopRepository;

    public Shop storeForShop(Shop shop, MultipartFile file) {
        validateUpload(file);

        try {
            Path shopDir = baseUploadDir().resolve("shop-" + shop.getId());
            Files.createDirectories(shopDir);

            deleteExistingFileIfPresent(shop);

            String originalName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "shop-seal");
            String extension = extensionOf(originalName);
            String storedName = "shop-seal-" + shop.getId() + "-" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
            Path target = shopDir.resolve(storedName);

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            shop.setSealOriginalFilename(originalName);
            shop.setSealStoredFilename(storedName);
            shop.setSealContentType(file.getContentType());
            shop.setSealUploadedAt(LocalDateTime.now());
            return shopRepository.save(shop);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store invoice seal.");
        }
    }

    public Resource loadForShop(Shop shop) {
        Path filePath = resolvePathForShop(shop);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Invoice seal not found.");
            }
            return resource;
        } catch (IOException ex) {
            throw new RuntimeException("Invoice seal not found.");
        }
    }

    public Path resolvePathForShop(Shop shop) {
        if (shop.getSealStoredFilename() == null || shop.getSealStoredFilename().isBlank()) {
            throw new RuntimeException("Invoice seal not found.");
        }
        return baseUploadDir()
                .resolve("shop-" + shop.getId())
                .resolve(shop.getSealStoredFilename())
                .normalize();
    }

    public Shop removeForShop(Shop shop) {
        deleteExistingFileIfPresent(shop);
        shop.setSealOriginalFilename(null);
        shop.setSealStoredFilename(null);
        shop.setSealContentType(null);
        shop.setSealUploadedAt(null);
        return shopRepository.save(shop);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Invoice seal file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("Invoice seal must be under 3 MB.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("Invoice seal must be JPG, PNG, or WEBP.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ENGLISH))) {
            throw new RuntimeException("Unsupported invoice seal type.");
        }
    }

    private void deleteExistingFileIfPresent(Shop shop) {
        if (shop.getSealStoredFilename() == null || shop.getSealStoredFilename().isBlank()) {
            return;
        }
        try {
            Path filePath = baseUploadDir()
                    .resolve("shop-" + shop.getId())
                    .resolve(shop.getSealStoredFilename());
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }

    private Path baseUploadDir() {
        return Paths.get(System.getProperty("user.dir"), "uploads", "shop-seals");
    }

    private String extensionOf(String fileName) {
        String cleaned = fileName != null ? fileName.trim() : "";
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            return "";
        }
        return cleaned.substring(dot + 1).toLowerCase(Locale.ENGLISH);
    }
}
