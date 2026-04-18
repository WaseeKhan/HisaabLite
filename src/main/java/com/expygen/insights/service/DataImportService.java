package com.expygen.insights.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.expygen.dto.PurchaseEntryForm;
import com.expygen.dto.PurchaseLineForm;
import com.expygen.entity.Product;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.entity.PurchaseBatch;
import com.expygen.insights.dto.OpeningStockImportResultDto;
import com.expygen.insights.dto.OpeningStockImportRowDto;
import com.expygen.insights.dto.ProductImportResultDto;
import com.expygen.insights.dto.ProductImportRowDto;
import com.expygen.insights.dto.SupplierImportResultDto;
import com.expygen.insights.dto.SupplierImportRowDto;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.SupplierRepository;
import com.expygen.service.PlanLimitService;
import com.expygen.service.PurchaseService;
import com.expygen.service.SupplierService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataImportService {

    private static final int MAX_IMPORT_ROWS = 500;
    private static final List<String> NAME_HEADERS = List.of("medicine name", "name", "product name");
    private static final List<String> SALE_PRICE_HEADERS = List.of("sale price", "price", "sp");

    private final ProductRepository productRepository;
    private final PlanLimitService planLimitService;
    private final PurchaseBatchRepository purchaseBatchRepository;
    private final PurchaseService purchaseService;
    private final SupplierRepository supplierRepository;
    private final SupplierService supplierService;

    public ProductImportResultDto importProducts(Shop shop, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Choose a CSV file before starting product import.");
        }

        List<List<String>> parsedRows = parseCsv(file);
        if (parsedRows.isEmpty()) {
            throw new RuntimeException("The uploaded CSV file is empty.");
        }

        Map<String, Integer> headerIndex = buildHeaderIndex(parsedRows.get(0));
        requireHeader(headerIndex, NAME_HEADERS, "Medicine Name");
        requireHeader(headerIndex, SALE_PRICE_HEADERS, "Sale Price");

        List<ProductImportRowDto> rowResults = new ArrayList<>();
        int importedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        int processedRows = 0;

        long activeProductCount = productRepository.countByShopAndActiveTrue(shop);
        int productLimit = planLimitService.getProductLimit(shop);

        Set<String> seenBarcodes = new HashSet<>();
        Set<String> seenNames = new HashSet<>();

        for (int index = 1; index < parsedRows.size(); index++) {
            List<String> row = parsedRows.get(index);
            if (row.stream().allMatch(cell -> !StringUtils.hasText(cell))) {
                continue;
            }

            processedRows++;
            if (processedRows > MAX_IMPORT_ROWS) {
                rowResults.add(ProductImportRowDto.builder()
                        .rowNumber(index + 1)
                        .status("FAILED")
                        .message("Import supports up to " + MAX_IMPORT_ROWS + " product rows per run.")
                        .build());
                failedCount++;
                break;
            }

            String medicineName = readCell(row, headerIndex, NAME_HEADERS);
            String barcode = normalizeText(readCell(row, headerIndex, "barcode"));
            ProductImportRowDto.ProductImportRowDtoBuilder resultBuilder = ProductImportRowDto.builder()
                    .rowNumber(index + 1)
                    .medicineName(medicineName)
                    .barcode(barcode);

            try {
                if (!StringUtils.hasText(medicineName)) {
                    throw new IllegalArgumentException("Medicine name is required.");
                }

                String normalizedName = medicineName.trim();
                String nameKey = normalizedName.toLowerCase(Locale.ENGLISH);
                if (!seenNames.add(nameKey)) {
                    rowResults.add(resultBuilder
                            .status("SKIPPED")
                            .message("Same medicine name appears more than once in this file. Keep one product row only.")
                            .build());
                    skippedCount++;
                    continue;
                }

                if (StringUtils.hasText(barcode)) {
                    String barcodeKey = barcode.toLowerCase(Locale.ENGLISH);
                    if (!seenBarcodes.add(barcodeKey)) {
                        rowResults.add(resultBuilder
                                .status("SKIPPED")
                                .message("This barcode appears more than once in the uploaded file.")
                                .build());
                        skippedCount++;
                        continue;
                    }
                    if (productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, barcode)) {
                        rowResults.add(resultBuilder
                                .status("SKIPPED")
                                .message("Barcode already exists in this shop. That medicine was left unchanged.")
                                .build());
                        skippedCount++;
                        continue;
                    }
                }

                Optional<Product> existingByName = productRepository.findByShopAndNameIgnoreCaseAndActiveTrue(shop, normalizedName);
                if (existingByName.isPresent()) {
                    rowResults.add(resultBuilder
                            .status("SKIPPED")
                            .message("Medicine already exists in this shop. Duplicate product was not created.")
                            .build());
                    skippedCount++;
                    continue;
                }

                if (productLimit != -1 && activeProductCount >= productLimit) {
                    rowResults.add(resultBuilder
                            .status("SKIPPED")
                            .message("Product plan limit reached for this shop. Remaining rows were not imported.")
                            .build());
                    skippedCount++;
                    continue;
                }

                Product product = Product.builder()
                        .name(normalizedName)
                        .genericName(normalizeText(readCell(row, headerIndex, "generic name", "salt", "composition")))
                        .manufacturer(normalizeText(readCell(row, headerIndex, "manufacturer", "brand")))
                        .barcode(barcode)
                        .packSize(normalizeText(readCell(row, headerIndex, "pack size", "pack", "unit pack")))
                        .price(parseRequiredDecimal(readCell(row, headerIndex, SALE_PRICE_HEADERS), "Sale Price"))
                        .mrp(parseOptionalDecimal(readCell(row, headerIndex, "mrp")))
                        .purchasePrice(parseOptionalDecimal(readCell(row, headerIndex, "purchase price", "cp", "cost price")))
                        .stockQuantity(parseOptionalInteger(readCell(row, headerIndex, "stock quantity", "opening stock", "qty"), 0))
                        .minStock(parseOptionalInteger(readCell(row, headerIndex, "min stock", "minimum stock"), 5))
                        .gstPercent(parseOptionalInteger(readCell(row, headerIndex, "gst %", "gst", "gst percent"), 0))
                        .prescriptionRequired(parseBoolean(readCell(row, headerIndex, "prescription required", "rx required", "rx")))
                        .description(normalizeText(readCell(row, headerIndex, "notes", "description")))
                        .shop(shop)
                        .active(true)
                        .build();

                productRepository.save(product);
                activeProductCount++;
                importedCount++;
                rowResults.add(resultBuilder
                        .status("IMPORTED")
                        .message("Medicine imported and ready for pricing, barcode, and stock workflows.")
                        .build());
            } catch (Exception ex) {
                failedCount++;
                rowResults.add(resultBuilder
                        .status("FAILED")
                        .message(ex.getMessage())
                        .build());
            }
        }

        return ProductImportResultDto.builder()
                .fileName(file.getOriginalFilename())
                .totalRows(processedRows)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .rows(rowResults)
                .build();
    }

    public String buildProductTemplateCsv() {
        return String.join("\n",
                "Medicine Name,Generic Name,Manufacturer,Barcode,Pack Size,Sale Price,MRP,Purchase Price,Stock Quantity,Min Stock,GST %,Prescription Required,Notes",
                "\"Dolo 650\",\"Paracetamol\",\"Micro Labs\",\"8901234567001\",\"15 tablets\",32,34,24,0,10,12,No,\"Fast moving fever medicine\"",
                "\"Azithral 500\",\"Azithromycin\",\"Alembic\",\"8901234567002\",\"3 tablets\",96,102,72,0,5,12,Yes,\"Prescription only antibiotic\"")
                + "\n";
    }

    public OpeningStockImportResultDto importOpeningStock(Shop shop, User createdBy, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Choose a CSV file before starting opening stock import.");
        }
        if (createdBy == null || createdBy.getShop() == null || !shop.getId().equals(createdBy.getShop().getId())) {
            throw new RuntimeException("A valid shop user is required for opening stock import.");
        }

        List<List<String>> parsedRows = parseCsv(file);
        if (parsedRows.isEmpty()) {
            throw new RuntimeException("The uploaded CSV file is empty.");
        }

        Map<String, Integer> headerIndex = buildHeaderIndex(parsedRows.get(0));
        requireAtLeastOneHeader(headerIndex, List.of("medicine name", "barcode"), "Medicine Name or Barcode");
        requireHeader(headerIndex, List.of("batch number", "batch"), "Batch Number");
        requireHeader(headerIndex, List.of("quantity", "qty", "opening qty"), "Quantity");
        requireHeader(headerIndex, List.of("purchase price", "cp", "cost price"), "Purchase Price");

        List<OpeningStockImportRowDto> rowResults = new ArrayList<>();
        List<OpeningStockCandidate> validCandidates = new ArrayList<>();
        Set<String> fileBatchKeys = new HashSet<>();
        int processedRows = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (int index = 1; index < parsedRows.size(); index++) {
            List<String> row = parsedRows.get(index);
            if (row.stream().allMatch(cell -> !StringUtils.hasText(cell))) {
                continue;
            }

            processedRows++;
            if (processedRows > MAX_IMPORT_ROWS) {
                rowResults.add(OpeningStockImportRowDto.builder()
                        .rowNumber(index + 1)
                        .status("FAILED")
                        .message("Opening stock import supports up to " + MAX_IMPORT_ROWS + " rows per run.")
                        .build());
                failedCount++;
                break;
            }

            String medicineName = normalizeText(readCell(row, headerIndex, "medicine name", "name", "product name"));
            String barcode = normalizeText(readCell(row, headerIndex, "barcode"));
            String batchNumber = normalizeText(readCell(row, headerIndex, "batch number", "batch"));
            OpeningStockImportRowDto.OpeningStockImportRowDtoBuilder resultBuilder = OpeningStockImportRowDto.builder()
                    .rowNumber(index + 1)
                    .medicineName(medicineName)
                    .barcode(barcode)
                    .batchNumber(batchNumber);

            try {
                Product product = resolveImportProduct(shop, medicineName, barcode);
                if (!StringUtils.hasText(batchNumber)) {
                    throw new IllegalArgumentException("Batch number is required.");
                }

                String fileBatchKey = product.getId() + "::" + batchNumber.toLowerCase(Locale.ENGLISH);
                if (!fileBatchKeys.add(fileBatchKey)) {
                    rowResults.add(resultBuilder
                            .medicineName(product.getName())
                            .status("SKIPPED")
                            .message("This product and batch combination appears more than once in the uploaded file.")
                            .build());
                    skippedCount++;
                    continue;
                }

                if (purchaseBatchRepository.findByShopAndProductAndBatchNumberIgnoreCaseAndActiveTrue(shop, product, batchNumber).isPresent()) {
                    rowResults.add(resultBuilder
                            .medicineName(product.getName())
                            .status("SKIPPED")
                            .message("This batch already exists for the selected medicine in your live stock.")
                            .build());
                    skippedCount++;
                    continue;
                }

                Integer quantity = parseOptionalInteger(readCell(row, headerIndex, "quantity", "qty", "opening qty"), 0);
                if (quantity == null || quantity <= 0) {
                    throw new IllegalArgumentException("Quantity must be greater than zero.");
                }

                BigDecimal purchasePrice = parseRequiredDecimal(readCell(row, headerIndex, "purchase price", "cp", "cost price"), "Purchase Price");
                BigDecimal salePrice = parseOptionalDecimal(readCell(row, headerIndex, "sale price", "sp", "price"));
                BigDecimal mrp = parseOptionalDecimal(readCell(row, headerIndex, "mrp"));
                LocalDate purchaseDate = parseOptionalDate(readCell(row, headerIndex, "purchase date", "stock date"));
                LocalDate expiryDate = parseOptionalDate(readCell(row, headerIndex, "expiry date", "expiry"));
                String supplierName = normalizeText(readCell(row, headerIndex, "supplier name", "supplier"));
                String invoiceNumber = normalizeText(readCell(row, headerIndex, "supplier invoice number", "invoice number", "reference"));
                String notes = normalizeText(readCell(row, headerIndex, "notes"));

                validCandidates.add(OpeningStockCandidate.builder()
                        .rowNumber(index + 1)
                        .product(product)
                        .batchNumber(batchNumber)
                        .quantity(quantity)
                        .purchasePrice(purchasePrice)
                        .salePrice(salePrice)
                        .mrp(mrp)
                        .purchaseDate(purchaseDate != null ? purchaseDate : LocalDate.now())
                        .expiryDate(expiryDate)
                        .supplierName(StringUtils.hasText(supplierName) ? supplierName : "Opening Stock Import")
                        .invoiceNumber(invoiceNumber)
                        .notes(notes)
                        .build());

                rowResults.add(resultBuilder
                        .medicineName(product.getName())
                        .status("READY")
                        .message("Validated and queued for opening stock import.")
                        .build());
            } catch (Exception ex) {
                failedCount++;
                rowResults.add(resultBuilder
                        .status("FAILED")
                        .message(ex.getMessage())
                        .build());
            }
        }

        Map<String, List<OpeningStockCandidate>> groupedCandidates = new LinkedHashMap<>();
        for (OpeningStockCandidate candidate : validCandidates) {
            String groupKey = candidate.getSupplierName() + "|" + candidate.getPurchaseDate() + "|" + normalizeText(candidate.getInvoiceNumber());
            groupedCandidates.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(candidate);
        }

        int importedCount = 0;
        for (List<OpeningStockCandidate> group : groupedCandidates.values()) {
            PurchaseEntryForm form = new PurchaseEntryForm();
            OpeningStockCandidate anchor = group.get(0);
            form.setPurchaseDate(anchor.getPurchaseDate());
            form.setSupplierName(anchor.getSupplierName());
            form.setSupplierInvoiceNumber(anchor.getInvoiceNumber());
            form.setNotes(StringUtils.hasText(anchor.getNotes()) ? anchor.getNotes() : "Opening stock imported from Data Import Desk");

            List<PurchaseLineForm> items = new ArrayList<>();
            for (OpeningStockCandidate candidate : group) {
                PurchaseLineForm line = new PurchaseLineForm();
                line.setProductId(candidate.getProduct().getId());
                line.setBatchNumber(candidate.getBatchNumber());
                line.setExpiryDate(candidate.getExpiryDate());
                line.setQuantity(candidate.getQuantity());
                line.setPurchasePrice(candidate.getPurchasePrice());
                line.setSalePrice(candidate.getSalePrice());
                line.setMrp(candidate.getMrp());
                items.add(line);
            }
            form.setItems(items);

            try {
                purchaseService.recordPurchase(form, shop, createdBy);
                for (OpeningStockCandidate candidate : group) {
                    importedCount++;
                    updateOpeningStockRowStatus(rowResults, candidate.getRowNumber(), "IMPORTED",
                            "Opening stock imported into batch inventory successfully.");
                }
            } catch (Exception ex) {
                for (OpeningStockCandidate candidate : group) {
                    failedCount++;
                    updateOpeningStockRowStatus(rowResults, candidate.getRowNumber(), "FAILED", ex.getMessage());
                }
            }
        }

        long readyRows = rowResults.stream().filter(row -> "READY".equals(row.getStatus())).count();
        failedCount += (int) readyRows;
        rowResults.stream()
                .filter(row -> "READY".equals(row.getStatus()))
                .forEach(row -> {
                    row.setStatus("FAILED");
                    row.setMessage("Opening stock import did not finish for this row.");
                });

        return OpeningStockImportResultDto.builder()
                .fileName(file.getOriginalFilename())
                .totalRows(processedRows)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .rows(rowResults)
                .build();
    }

    public String buildOpeningStockTemplateCsv() {
        return String.join("\n",
                "Medicine Name,Barcode,Batch Number,Expiry Date,Quantity,Purchase Price,Sale Price,MRP,Supplier Name,Purchase Date,Supplier Invoice Number,Notes",
                "\"Dolo 650\",\"8901234567001\",\"DL650A1\",2027-10-31,40,24,32,34,\"Opening Stock Import\",2026-04-13,\"OPEN-001\",\"Imported during go-live\"",
                "\"Azithral 500\",\"8901234567002\",\"AZ500B1\",2027-08-31,18,72,96,102,\"Opening Stock Import\",2026-04-13,\"OPEN-001\",\"Prescription batch\"")
                + "\n";
    }

    public SupplierImportResultDto importSuppliers(Shop shop, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Choose a CSV file before starting supplier import.");
        }

        List<List<String>> parsedRows = parseCsv(file);
        if (parsedRows.isEmpty()) {
            throw new RuntimeException("The uploaded CSV file is empty.");
        }

        Map<String, Integer> headerIndex = buildHeaderIndex(parsedRows.get(0));
        requireHeader(headerIndex, List.of("supplier name", "name"), "Supplier Name");

        List<SupplierImportRowDto> rowResults = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        int processedRows = 0;
        int importedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (int index = 1; index < parsedRows.size(); index++) {
            List<String> row = parsedRows.get(index);
            if (row.stream().allMatch(cell -> !StringUtils.hasText(cell))) {
                continue;
            }

            processedRows++;
            if (processedRows > MAX_IMPORT_ROWS) {
                rowResults.add(SupplierImportRowDto.builder()
                        .rowNumber(index + 1)
                        .status("FAILED")
                        .message("Supplier import supports up to " + MAX_IMPORT_ROWS + " rows per run.")
                        .build());
                failedCount++;
                break;
            }

            String supplierName = normalizeText(readCell(row, headerIndex, "supplier name", "name"));
            SupplierImportRowDto.SupplierImportRowDtoBuilder resultBuilder = SupplierImportRowDto.builder()
                    .rowNumber(index + 1)
                    .supplierName(supplierName)
                    .phone(normalizeText(readCell(row, headerIndex, "phone", "mobile", "contact number")))
                    .gstNumber(normalizeText(readCell(row, headerIndex, "gst number", "gst")))
                    .contactPerson(normalizeText(readCell(row, headerIndex, "contact person", "contact")));

            try {
                if (!StringUtils.hasText(supplierName)) {
                    throw new IllegalArgumentException("Supplier name is required.");
                }

                String normalizedName = supplierName.trim();
                String nameKey = normalizedName.toLowerCase(Locale.ENGLISH);
                if (!seenNames.add(nameKey)) {
                    rowResults.add(resultBuilder
                            .status("SKIPPED")
                            .message("This supplier appears more than once in the uploaded file.")
                            .build());
                    skippedCount++;
                    continue;
                }

                if (supplierRepository.findByShopAndNameIgnoreCase(shop, normalizedName).isPresent()) {
                    rowResults.add(resultBuilder
                            .status("SKIPPED")
                            .message("Supplier already exists in this shop. Duplicate record was not created.")
                            .build());
                    skippedCount++;
                    continue;
                }

                com.expygen.dto.SupplierForm form = new com.expygen.dto.SupplierForm();
                form.setName(normalizedName);
                form.setContactPerson(normalizeText(readCell(row, headerIndex, "contact person", "contact")));
                form.setPhone(normalizeText(readCell(row, headerIndex, "phone", "mobile", "contact number")));
                form.setGstNumber(normalizeText(readCell(row, headerIndex, "gst number", "gst")));
                form.setAddress(normalizeText(readCell(row, headerIndex, "address")));
                form.setNotes(normalizeText(readCell(row, headerIndex, "notes")));

                supplierService.saveSupplier(shop, form);
                importedCount++;
                rowResults.add(resultBuilder
                        .status("IMPORTED")
                        .message("Supplier imported and ready for purchase entry and reporting.")
                        .build());
            } catch (Exception ex) {
                failedCount++;
                rowResults.add(resultBuilder
                        .status("FAILED")
                        .message(ex.getMessage())
                        .build());
            }
        }

        return SupplierImportResultDto.builder()
                .fileName(file.getOriginalFilename())
                .totalRows(processedRows)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .rows(rowResults)
                .build();
    }

    public String buildSupplierTemplateCsv() {
        return String.join("\n",
                "Supplier Name,Contact Person,Phone,GST Number,Address,Notes",
                "\"MediNova Distributors\",\"Rahul Sharma\",\"9876543210\",\"27AABCM1234D1Z9\",\"Andheri East, Mumbai\",\"Preferred pharma supplier\"",
                "\"WellCare Wholesale\",\"Neha Jain\",\"9988776655\",\"27AACCW4567M1Z2\",\"Thane West, Mumbai\",\"Fast delivery for urgent restock\"")
                + "\n";
    }

    private List<List<String>> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return parseCsvContent(builder.toString());
        } catch (IOException e) {
            throw new RuntimeException("Could not read the uploaded CSV file.");
        }
    }

    private List<List<String>> parseCsvContent(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (current == '"') {
                if (inQuotes && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    currentCell.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (current == ',' && !inQuotes) {
                currentRow.add(currentCell.toString().trim());
                currentCell.setLength(0);
                continue;
            }

            if ((current == '\n' || current == '\r') && !inQuotes) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                currentRow.add(currentCell.toString().trim());
                currentCell.setLength(0);
                if (!currentRow.isEmpty() && currentRow.stream().anyMatch(StringUtils::hasText)) {
                    rows.add(new ArrayList<>(currentRow));
                }
                currentRow.clear();
                continue;
            }

            currentCell.append(current);
        }

        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString().trim());
            if (currentRow.stream().anyMatch(StringUtils::hasText)) {
                rows.add(currentRow);
            }
        }

        return rows;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headerRow) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            headerIndex.put(normalizeHeader(headerRow.get(index)), index);
        }
        return headerIndex;
    }

    private void requireHeader(Map<String, Integer> headerIndex, List<String> acceptedHeaders, String displayName) {
        boolean exists = acceptedHeaders.stream().map(this::normalizeHeader).anyMatch(headerIndex::containsKey);
        if (!exists) {
            throw new RuntimeException(displayName + " column is missing from the CSV template.");
        }
    }

    private String readCell(List<String> row, Map<String, Integer> headerIndex, List<String> acceptedHeaders) {
        for (String header : acceptedHeaders) {
            String value = readCell(row, headerIndex, header);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String readCell(List<String> row, Map<String, Integer> headerIndex, String... acceptedHeaders) {
        for (String header : acceptedHeaders) {
            Integer index = headerIndex.get(normalizeHeader(header));
            if (index != null && index < row.size()) {
                return row.get(index);
            }
        }
        return null;
    }

    private String normalizeHeader(String header) {
        return header == null ? ""
                : header.replace("\uFEFF", "")
                        .trim()
                        .toLowerCase(Locale.ENGLISH)
                        .replace("_", " ")
                        .replaceAll("\\s+", " ");
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal parseRequiredDecimal(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return parseDecimal(value, label);
    }

    private BigDecimal parseOptionalDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return parseDecimal(value, "Amount");
    }

    private BigDecimal parseDecimal(String value, String label) {
        try {
            String normalized = value.replace("₹", "").replace(",", "").trim();
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " is not a valid number.");
        }
    }

    private Integer parseOptionalInteger(String value, int fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Quantity, stock, or GST value is not valid.");
        }
    }

    private boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        return normalized.equals("yes")
                || normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("rx")
                || normalized.equals("required");
    }

    private void requireAtLeastOneHeader(Map<String, Integer> headerIndex, List<String> acceptedHeaders, String displayName) {
        boolean exists = acceptedHeaders.stream().map(this::normalizeHeader).anyMatch(headerIndex::containsKey);
        if (!exists) {
            throw new RuntimeException(displayName + " column is missing from the CSV template.");
        }
    }

    private Product resolveImportProduct(Shop shop, String medicineName, String barcode) {
        if (StringUtils.hasText(barcode)) {
            List<Product> barcodeMatches = productRepository.searchProducts(shop, barcode).stream()
                    .filter(product -> StringUtils.hasText(product.getBarcode()) && product.getBarcode().equalsIgnoreCase(barcode))
                    .toList();
            if (!barcodeMatches.isEmpty()) {
                return barcodeMatches.get(0);
            }
        }

        if (StringUtils.hasText(medicineName)) {
            return productRepository.findByShopAndNameIgnoreCaseAndActiveTrue(shop, medicineName.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Medicine was not found in this shop. Import product master first."));
        }

        throw new IllegalArgumentException("Each row needs either Medicine Name or Barcode.");
    }

    private LocalDate parseOptionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Date fields must use yyyy-MM-dd format.");
        }
    }

    private void updateOpeningStockRowStatus(List<OpeningStockImportRowDto> rows, int rowNumber, String status, String message) {
        rows.stream()
                .filter(row -> row.getRowNumber() == rowNumber)
                .findFirst()
                .ifPresent(row -> {
                    row.setStatus(status);
                    row.setMessage(message);
                });
    }

    @lombok.Builder
    @lombok.Getter
    private static class OpeningStockCandidate {
        private final int rowNumber;
        private final Product product;
        private final String batchNumber;
        private final Integer quantity;
        private final BigDecimal purchasePrice;
        private final BigDecimal salePrice;
        private final BigDecimal mrp;
        private final LocalDate purchaseDate;
        private final LocalDate expiryDate;
        private final String supplierName;
        private final String invoiceNumber;
        private final String notes;
    }
}
