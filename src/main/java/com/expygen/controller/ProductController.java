package com.expygen.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.UserRepository;
import com.expygen.admin.service.AuditService;
import com.expygen.dto.ProductBatchVisibility;
import com.expygen.service.BatchInventoryVisibilityService;
import com.expygen.service.BarcodeLabelService;
import com.expygen.service.ProductBarcodeService;
import com.expygen.service.ProductService;
import com.expygen.service.PlanLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final PlanLimitService planLimitService;
    private final AuditService auditService;
    private final PurchaseBatchRepository purchaseBatchRepository;
    private final BatchInventoryVisibilityService batchInventoryVisibilityService;
    private final BarcodeLabelService barcodeLabelService;
    private final ProductBarcodeService productBarcodeService;

    private static final int PAGE_SIZE = 20;

    // Helper method to get current user
    private User getUser(Authentication authentication) {
        return userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // LIST PRODUCTS WITH SEARCH (Latest first)
    @GetMapping
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            Authentication authentication) {

        User user = getUser(authentication);
        Shop shop = user.getShop();

        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
        Page<Product> productPage;

        if (keyword != null && !keyword.trim().isEmpty()) {
            productPage = productService.searchProductsWithPagination(keyword.trim(), user.getShop(), pageable);
            model.addAttribute("keyword", keyword);
        } else {
            productPage = productRepository.findByShopAndActiveTrue(user.getShop(), pageable);
        }

        // Get product limit stats - Count ONLY ACTIVE products
        long currentProductCount = productRepository.countByShopAndActiveTrue(shop);
        int productLimit = planLimitService.getProductLimit(shop);
        
        // Handle unlimited (-1) correctly
        boolean canAddMore = (productLimit == -1) || (currentProductCount < productLimit);
        String displayProductLimit = (productLimit == -1) ? "Unlimited" : String.valueOf(productLimit);

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", productPage.getNumber());
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalItems", productPage.getTotalElements());
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        model.addAttribute("user", user);
        model.addAttribute("shop", shop);
        model.addAttribute("currentProductCount", currentProductCount);
        model.addAttribute("productLimit", displayProductLimit);
        model.addAttribute("productLimitRaw", productLimit);
        model.addAttribute("canAddMore", canAddMore);

        PlanType planType = user.getShop().getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);

        return "products";
    }
    
    // AJAX ENDPOINT FOR LIVE SEARCH (Latest first)
    @GetMapping("/search-live")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchProductsLive(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication) {

        try {
            User user = getUser(authentication);

            // Sort by id descending (latest first)
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
            Page<Product> productPage;

            if (keyword != null && !keyword.trim().isEmpty()) {
                productPage = productService.searchProductsWithPagination(keyword.trim(),
                        user.getShop(), pageable);
            } else {
                productPage = productRepository.findByShopAndActiveTrue(user.getShop(), pageable);
            }

            Map<Long, ProductBatchVisibility> batchVisibility = batchInventoryVisibilityService
                    .summarizeProducts(user.getShop(), productPage.getContent());
            List<Map<String, Object>> productSnapshots = productPage.getContent().stream()
                    .map(product -> snapshotProduct(product, batchVisibility.get(product.getId())))
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("products", productSnapshots);
            response.put("currentPage", productPage.getNumber());
            response.put("totalPages", productPage.getTotalPages());
            response.put("totalItems", productPage.getTotalElements());
            response.put("pageSize", productPage.getSize());
            response.put("hasNext", productPage.hasNext());
            response.put("hasPrevious", productPage.hasPrevious());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching products", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // EXPORT PRODUCTS TO CSV
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportProducts(Authentication authentication) {
        try {
            User user = getUser(authentication);
            List<Product> products = productRepository.findByShopAndActiveTrue(user.getShop());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(out);
            
            // CSV Header
            writer.println("ID,Medicine Name,Generic Name,Manufacturer,Barcode,Pack Size,Sale Price,MRP,Purchase Price,Stock Quantity,Min Stock,GST %,Prescription Required,Notes,Status");
            
            // CSV Data
            for (Product p : products) {
                writer.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%s,%s,%d,%d,%d,%s,\"%s\",%s\n",
                    p.getId(),
                    escapeCsv(p.getName()),
                    escapeCsv(p.getGenericName() != null ? p.getGenericName() : ""),
                    escapeCsv(p.getManufacturer() != null ? p.getManufacturer() : ""),
                    escapeCsv(p.getBarcode() != null ? p.getBarcode() : ""),
                    escapeCsv(p.getPackSize() != null ? p.getPackSize() : ""),
                    p.getPrice(),
                    p.getMrp() != null ? p.getMrp().toPlainString() : "",
                    p.getPurchasePrice() != null ? p.getPurchasePrice().toPlainString() : "",
                    p.getStockQuantity(),
                    p.getMinStock(),
                    p.getGstPercent() != null ? p.getGstPercent() : 0,
                    p.isPrescriptionRequired() ? "Yes" : "No",
                    escapeCsv(p.getDescription() != null ? p.getDescription() : ""),
                    p.getStockQuantity() <= p.getMinStock() ? "Low Stock" : "In Stock"
                );
            }
            writer.flush();
            
            byte[] csvBytes = out.toByteArray();
            
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=products_export.csv")
                    .header("Content-Length", String.valueOf(csvBytes.length))
                    .body(csvBytes);
                    
        } catch (Exception e) {
            log.error("Error exporting products", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    // DUPLICATE PRODUCT - WITH LIMIT CHECK
    @PostMapping("/duplicate")
    @ResponseBody
    public ResponseEntity<Map<String, String>> duplicateProduct(
            @RequestParam Long id,
            Authentication authentication) {
        
        try {
            User user = getUser(authentication);
            Shop shop = user.getShop();
            
            // Check product limit before duplicating
            int maxLimit = planLimitService.getProductLimit(shop);
            
            // Skip limit check if unlimited (-1)
            if (maxLimit != -1 && !planLimitService.canAddProduct(shop)) {
                long currentCount = productRepository.countByShopAndActiveTrue(shop);          
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Product limit reached! Your " + shop.getPlanType() +
                    " plan allows maximum " + maxLimit + " products. " +
                    "You currently have " + currentCount + " products."
                ));
            }
            
            Product original = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Check authorization
            if (!original.getShop().getId().equals(shop.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            // Create duplicate
            Product duplicate = new Product();
            duplicate.setName(original.getName() + " (Copy)");
            duplicate.setDescription(original.getDescription());
            duplicate.setBarcode(null);
            duplicate.setGenericName(original.getGenericName());
            duplicate.setManufacturer(original.getManufacturer());
            duplicate.setPackSize(original.getPackSize());
            duplicate.setPrice(original.getPrice());
            duplicate.setMrp(original.getMrp());
            duplicate.setPurchasePrice(original.getPurchasePrice());
            duplicate.setStockQuantity(0);
            duplicate.setMinStock(original.getMinStock());
            duplicate.setGstPercent(original.getGstPercent());
            duplicate.setPrescriptionRequired(original.isPrescriptionRequired());
            duplicate.setShop(shop);
            duplicate.setActive(true);

            Product savedDuplicate = productRepository.save(duplicate);
            auditService.logAction(
                    user.getUsername(),
                    user.getRole().name(),
                    shop,
                    "PRODUCT_DUPLICATED",
                    "Product",
                    savedDuplicate.getId(),
                    "SUCCESS",
                    snapshotProduct(original),
                    snapshotProduct(savedDuplicate),
                    "Product duplicated from existing product");
            
            return ResponseEntity.ok(Map.of("message", "Product duplicated successfully"));
            
        } catch (Exception e) {
            log.error("Error duplicating product", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to duplicate product"));
        }
    }

    // BULK DELETE PRODUCTS
    @PostMapping("/bulk-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkDeleteProducts(
            @RequestParam List<Long> ids,
            Authentication authentication) {
        
        try {
            User user = getUser(authentication);
            if (user.getRole() != com.expygen.entity.Role.OWNER) {
                return ResponseEntity.status(403).body(Map.of("error", "Only owners can delete medicines"));
            }
            int deletedCount = 0;
            
            for (Long id : ids) {
                Product product = productRepository.findById(id).orElse(null);
                if (product != null && product.getShop().getId().equals(user.getShop().getId())) {
                    Product safeProduct = loadVersionSafeProduct(id);
                    safeProduct.setActive(false);
                    productRepository.save(safeProduct);
                    deletedCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", deletedCount + " product(s) deleted successfully");
            response.put("deletedCount", deletedCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error bulk deleting products", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete products"));
        }
    }

    // NEW PRODUCT FORM - WITH LIMIT CHECK (FIXED for unlimited)
    @GetMapping("/new")
    public String newProductForm(Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        // Get current active product count
        long currentCount = productRepository.countByShopAndActiveTrue(shop);
        int maxLimit = planLimitService.getProductLimit(shop);
        
        log.info("Product limit check - Current active: {}, Max limit: {}", currentCount, maxLimit);
        
        // Check if unlimited OR limit not reached
        boolean isUnlimited = (maxLimit == -1);
        
        if (!isUnlimited && currentCount >= maxLimit) {
            redirectAttributes.addFlashAttribute("error",
                    "Product limit reached! Your " + shop.getPlanType() +
                    " plan allows maximum " + maxLimit + " products. " +
                    "You currently have " + currentCount + " active products. " +
                    "Please upgrade your plan to add more products.");
            
            return "redirect:/products";
        }

        // If limit not reached or unlimited, show product form
        model.addAttribute("product", new Product());
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        
        PlanType planType = shop.getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);
        model.addAttribute("productLimit", isUnlimited ? "Unlimited" : maxLimit);
        model.addAttribute("productLimitRaw", maxLimit);
        model.addAttribute("currentProductCount", currentCount);
        model.addAttribute("canAddMore", isUnlimited || currentCount < maxLimit);

        return "product-form";
    }
    
    // SAVE OR UPDATE PRODUCT - WITH LIMIT CHECK FOR NEW PRODUCTS (FIXED for unlimited)
    @PostMapping("/save")
    public String saveOrUpdateProduct(@ModelAttribute Product product,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = getUser(authentication);
        Shop shop = user.getShop();
        
        // Check if this is a new product (id is null)
        boolean isNewProduct = product.getId() == null;
        Product productToSave = product;
        Map<String, Object> oldProductState = null;
        
        if (isNewProduct) {
            int maxLimit = planLimitService.getProductLimit(shop);
            boolean isUnlimited = (maxLimit == -1);
            
            // Check product limit only if not unlimited
            if (!isUnlimited && !planLimitService.canAddProduct(shop)) {
                long currentCount = productRepository.countByShopAndActiveTrue(shop);
                
                redirectAttributes.addFlashAttribute("error",
                        "Product limit reached! Your " + shop.getPlanType() +
                        " plan allows maximum " + maxLimit + " products. " +
                        "You currently have " + currentCount + " products.");
                
                return "redirect:/products";
            }

            applyEditableFields(product, product);
            if (hasDuplicateBarcode(product, shop)) {
                model.addAttribute("error",
                        "Barcode already exists for another medicine in your shop. Use a unique code before saving.");
                populateProductFormModel(model, user, shop, product);
                return "product-form";
            }
        } else {
            Product existingProduct = loadVersionSafeProduct(product.getId());

            if (!existingProduct.getShop().getId().equals(shop.getId())) {
                throw new RuntimeException("Unauthorized access");
            }

            oldProductState = snapshotProduct(existingProduct);
            applyEditableFields(product, existingProduct);
            int liveBatchStock = safeBatchStock(existingProduct);
            if (existingProduct.getStockQuantity() < liveBatchStock) {
                model.addAttribute(
                        "error",
                        "Stock quantity cannot be lower than live batch stock (" + liveBatchStock
                                + "). Use Purchases to manage batch inventory.");
                populateProductFormModel(model, user, shop, existingProduct);
                return "product-form";
            }
            if (hasDuplicateBarcode(existingProduct, shop)) {
                model.addAttribute("error",
                        "Barcode already exists for another medicine in your shop. Use a unique code before saving.");
                populateProductFormModel(model, user, shop, existingProduct);
                return "product-form";
            }

            productToSave = existingProduct;
        }
        
        productToSave.setShop(shop);
        productToSave.setActive(true);

        if (productToSave.getMinStock() == null) {
            productToSave.setMinStock(5);
        }
        if (productToSave.getGstPercent() == null) {
            productToSave.setGstPercent(0);
        }

        Product savedProduct = productRepository.save(productToSave);
        auditService.logAction(
                user.getUsername(),
                user.getRole().name(),
                shop,
                isNewProduct ? "PRODUCT_CREATED" : "PRODUCT_UPDATED",
                "Product",
                savedProduct.getId(),
                "SUCCESS",
                oldProductState,
                snapshotProduct(savedProduct),
                isNewProduct ? "Product created" : "Product updated");
        
        redirectAttributes.addFlashAttribute("success", 
            isNewProduct ? "Product added successfully!" : "Product updated successfully!");

        return "redirect:/products";
    }

    @GetMapping("/barcode-check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkBarcodeAvailability(
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) Long productId,
            Authentication authentication) {

        User user = getUser(authentication);
        Shop shop = user.getShop();
        String normalizedBarcode = normalizeBarcode(barcode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("normalizedBarcode", normalizedBarcode);

        if (!StringUtils.hasText(normalizedBarcode)) {
            response.put("available", true);
            response.put("state", "empty");
            response.put("message", "Add a manufacturer barcode now, or leave it blank and fill it later when stock arrives.");
            return ResponseEntity.ok(response);
        }

        boolean duplicate = productId == null
                ? productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, normalizedBarcode)
                : productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrueAndIdNot(shop, normalizedBarcode, productId);

        response.put("available", !duplicate);
        response.put("state", duplicate ? "duplicate" : "ready");
        response.put("message", duplicate
                ? "This barcode is already assigned to another active medicine in your shop."
                : "Barcode is unique for this shop and ready for scanner billing.");
        return ResponseEntity.ok(response);
    }

    // EDIT PRODUCT
    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = getUser(authentication);
        Shop shop = user.getShop();

        Product product = loadVersionSafeProduct(id);

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }

        long currentCount = productRepository.countByShopAndActiveTrue(shop);
        int maxLimit = planLimitService.getProductLimit(shop);
        boolean isUnlimited = (maxLimit == -1);

        model.addAttribute("product", product);
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        model.addAttribute("currentProductCount", currentCount);
        model.addAttribute("productLimit", isUnlimited ? "Unlimited" : maxLimit);
        model.addAttribute("productLimitRaw", maxLimit);
        model.addAttribute("canAddMore", isUnlimited || planLimitService.canAddProduct(shop));

        PlanType planType = shop.getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);

        return "product-form";
    }

    // DELETE PRODUCT (SOFT DELETE)
    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = getUser(authentication);
        if (user.getRole() != com.expygen.entity.Role.OWNER) {
            redirectAttributes.addFlashAttribute("error", "Access denied: only owners can delete medicines.");
            return "redirect:/products";
        }

        Product product = loadVersionSafeProduct(id);

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }
        
        String productName = product.getName();
        Map<String, Object> oldProductState = snapshotProduct(product);
        product.setActive(false);
        productRepository.save(product);
        auditService.logAction(
                user.getUsername(),
                user.getRole().name(),
                user.getShop(),
                "PRODUCT_DELETED",
                "Product",
                product.getId(),
                "SUCCESS",
                oldProductState,
                snapshotProduct(product),
                "Product soft deleted");
        
        redirectAttributes.addFlashAttribute("success", 
            "Product '" + productName + "' deleted successfully!");

        return "redirect:/products";
    }

    @GetMapping("/{id}/barcode-label")
    @ResponseBody
    public ResponseEntity<byte[]> downloadBarcodeLabel(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getUser(authentication);
        Product product = loadVersionSafeProduct(id);

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }
        if (!StringUtils.hasText(product.getBarcode())) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("Barcode is missing for this medicine.".getBytes());
        }

        byte[] pdfBytes = barcodeLabelService.generateProductLabel(product);
        String safeName = product.getName()
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + safeName + "-barcode-label.pdf\"")
                .body(pdfBytes);
    }

    @GetMapping("/barcode-sheet")
    @ResponseBody
    public ResponseEntity<byte[]> downloadBarcodeSheet(
            @RequestParam List<Long> ids,
            Authentication authentication) {

        User user = getUser(authentication);
        List<Product> selectedProducts = productRepository.findByShopAndActiveTrueAndIdIn(user.getShop(), ids).stream()
                .filter(product -> StringUtils.hasText(product.getBarcode()))
                .toList();

        if (selectedProducts.isEmpty()) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("Select at least one barcode-ready medicine to print labels.".getBytes());
        }

        byte[] pdfBytes = barcodeLabelService.generateProductLabelSheet(selectedProducts);

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"barcode-sheet.pdf\"")
                .body(pdfBytes);
    }

    @GetMapping("/{id}/barcode-sheet")
    @ResponseBody
    public ResponseEntity<byte[]> downloadRepeatedBarcodeSheet(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "SHEET_40") String size,
            Authentication authentication) {

        User user = getUser(authentication);
        Product product = loadVersionSafeProduct(id);

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }
        if (!StringUtils.hasText(product.getBarcode())) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("Barcode is missing for this medicine.".getBytes());
        }
        if (quantity < 1 || quantity > 500) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("Choose a label quantity between 1 and 500.".getBytes());
        }

        BarcodeLabelService.LabelSheetSize sheetSize;
        try {
            sheetSize = BarcodeLabelService.LabelSheetSize.fromInput(size);
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("Choose a valid label sheet: 20, 24, 40, 48, 65, or 80 per sheet.".getBytes());
        }

        byte[] pdfBytes = barcodeLabelService.generateRepeatedProductLabelSheet(product, quantity, sheetSize);
        String safeName = product.getName()
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + safeName + "-labels-" + quantity + "-" + sheetSize.getLabelsPerSheet() + "-per-sheet.pdf\"")
                .body(pdfBytes);
    }

    @PostMapping("/{id}/generate-barcode")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateInternalBarcode(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getUser(authentication);
        Product product = loadVersionSafeProduct(id);

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }
        if (StringUtils.hasText(product.getBarcode())) {
            return ResponseEntity.ok(Map.of(
                    "barcode", product.getBarcode(),
                    "message", "This medicine already has a barcode.",
                    "labelUrl", "/products/" + product.getId() + "/barcode-label",
                    "generated", false));
        }

        Map<String, Object> oldState = snapshotProduct(product);
        String generatedBarcode = productBarcodeService.generateInternalBarcode(product);
        product.setBarcode(generatedBarcode);
        Product savedProduct = productRepository.save(product);

        auditService.logAction(
                user.getUsername(),
                user.getRole().name(),
                user.getShop(),
                "PRODUCT_BARCODE_GENERATED",
                "Product",
                savedProduct.getId(),
                "SUCCESS",
                oldState,
                snapshotProduct(savedProduct),
                "Internal barcode generated for product");

        return ResponseEntity.ok(Map.of(
                "barcode", generatedBarcode,
                "message", "Internal barcode generated successfully.",
                "labelUrl", "/products/" + savedProduct.getId() + "/barcode-label",
                "generated", true));
    }

    private Map<String, Object> snapshotProduct(Product product) {
        return snapshotProduct(product, null);
    }

    private Map<String, Object> snapshotProduct(Product product, ProductBatchVisibility batchVisibility) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", product.getId());
        snapshot.put("name", product.getName());
        snapshot.put("description", product.getDescription());
        snapshot.put("barcode", product.getBarcode());
        snapshot.put("genericName", product.getGenericName());
        snapshot.put("manufacturer", product.getManufacturer());
        snapshot.put("packSize", product.getPackSize());
        snapshot.put("price", product.getPrice());
        snapshot.put("mrp", product.getMrp());
        snapshot.put("purchasePrice", product.getPurchasePrice());
        snapshot.put("stockQuantity", product.getStockQuantity());
        snapshot.put("minStock", product.getMinStock());
        snapshot.put("gstPercent", product.getGstPercent());
        snapshot.put("prescriptionRequired", product.isPrescriptionRequired());
        snapshot.put("active", product.isActive());
        snapshot.put("shopId", product.getShop() != null ? product.getShop().getId() : null);
        if (batchVisibility != null) {
            snapshot.put("batchManaged", batchVisibility.isBatchManaged());
            snapshot.put("activeBatchCount", batchVisibility.getActiveBatchCount());
            snapshot.put("liveBatchStock", batchVisibility.getLiveBatchStock());
            snapshot.put("sellableStock", batchVisibility.getSellableStock());
            snapshot.put("nearExpiryBatchCount", batchVisibility.getNearExpiryBatchCount());
            snapshot.put("expiredBatchCount", batchVisibility.getExpiredBatchCount());
            snapshot.put("nextSellableExpiryDate", batchVisibility.getNextSellableExpiryDate());
            snapshot.put("lowStock", batchVisibility.isLowStock());
        } else {
            snapshot.put("batchManaged", false);
            snapshot.put("activeBatchCount", 0);
            snapshot.put("liveBatchStock", 0);
            snapshot.put("sellableStock", product.getStockQuantity());
            snapshot.put("nearExpiryBatchCount", 0);
            snapshot.put("expiredBatchCount", 0);
            snapshot.put("nextSellableExpiryDate", null);
            snapshot.put("lowStock", product.getStockQuantity() <= product.getMinStock());
        }
        return snapshot;
    }

    private void applyEditableFields(Product source, Product target) {
        target.setName(normalizeText(source.getName()));
        target.setDescription(normalizeText(source.getDescription()));
        target.setBarcode(normalizeBarcode(source.getBarcode()));
        target.setGenericName(normalizeText(source.getGenericName()));
        target.setManufacturer(normalizeText(source.getManufacturer()));
        target.setPackSize(normalizeText(source.getPackSize()));
        target.setPrice(source.getPrice());
        target.setMrp(source.getMrp());
        target.setPurchasePrice(source.getPurchasePrice());
        target.setStockQuantity(source.getStockQuantity());
        target.setMinStock(source.getMinStock());
        target.setGstPercent(source.getGstPercent());
        target.setPrescriptionRequired(source.isPrescriptionRequired());
    }

    private boolean hasDuplicateBarcode(Product product, Shop shop) {
        if (!StringUtils.hasText(product.getBarcode())) {
            return false;
        }
        if (product.getId() == null) {
            return productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrue(shop, product.getBarcode());
        }
        return productRepository.existsByShopAndBarcodeIgnoreCaseAndActiveTrueAndIdNot(
                shop,
                product.getBarcode(),
                product.getId());
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeBarcode(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s+", "").trim() : null;
    }

    private int safeBatchStock(Product product) {
        Integer batchStock = purchaseBatchRepository.sumAvailableQuantityByProduct(product);
        return batchStock != null ? batchStock : 0;
    }

    private Product loadVersionSafeProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getVersion() == null) {
            productRepository.initializeVersionIfMissing(productId);
            productRepository.flush();
            product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
        }

        return product;
    }

    private void populateProductFormModel(Model model, User user, Shop shop, Product product) {
        long currentCount = productRepository.countByShopAndActiveTrue(shop);
        int maxLimit = planLimitService.getProductLimit(shop);
        boolean isUnlimited = (maxLimit == -1);

        model.addAttribute("product", product);
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        model.addAttribute("currentProductCount", currentCount);
        model.addAttribute("productLimit", isUnlimited ? "Unlimited" : maxLimit);
        model.addAttribute("productLimitRaw", maxLimit);
        model.addAttribute("canAddMore", isUnlimited || currentCount < maxLimit || product.getId() != null);

        PlanType planType = shop.getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);
    }
}
