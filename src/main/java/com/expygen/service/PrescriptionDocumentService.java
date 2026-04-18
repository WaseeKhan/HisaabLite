package com.expygen.service;

import com.expygen.entity.Sale;
import com.expygen.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrescriptionDocumentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final SaleRepository saleRepository;

    public Sale storeForSale(Sale sale, MultipartFile file) {
        validateUpload(file);

        try {
            Path shopDir = baseUploadDir()
                    .resolve("shop-" + sale.getShop().getId())
                    .resolve("sale-" + sale.getId());
            Files.createDirectories(shopDir);

            deleteExistingFileIfPresent(sale);

            String originalName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "prescription");
            String extension = extensionOf(originalName);
            String storedName = "rx-" + sale.getId() + "-" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
            Path target = shopDir.resolve(storedName);

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            sale.setPrescriptionDocumentPath(target.toAbsolutePath().toString());
            sale.setPrescriptionDocumentName(originalName);
            sale.setPrescriptionDocumentContentType(file.getContentType());
            return saleRepository.save(sale);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store prescription document");
        }
    }

    public Resource loadForSale(Sale sale) {
        if (sale.getPrescriptionDocumentPath() == null || sale.getPrescriptionDocumentPath().isBlank()) {
            throw new RuntimeException("Prescription document not found");
        }

        try {
            Path filePath = Paths.get(sale.getPrescriptionDocumentPath());
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Prescription document not found");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Prescription document not found");
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Prescription document is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("Prescription document must be under 5 MB");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("Prescription document must be PDF or image");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ENGLISH))) {
            throw new RuntimeException("Unsupported prescription document type");
        }
    }

    private void deleteExistingFileIfPresent(Sale sale) {
        if (sale.getPrescriptionDocumentPath() == null || sale.getPrescriptionDocumentPath().isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(sale.getPrescriptionDocumentPath()));
        } catch (IOException ignored) {
        }
    }

    private Path baseUploadDir() {
        return Paths.get(System.getProperty("user.dir"), "uploads", "prescriptions");
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
