package com.hisaablite.controller;

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

import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.admin.service.AuditService;
import com.hisaablite.service.ProductService;
import com.hisaablite.service.PlanLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
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

            Map<String, Object> response = new HashMap<>();
            response.put("products", productPage.getContent());
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
            writer.println("ID,Product Name,Description,Price,Stock Quantity,Min Stock,GST %,Status");
            
            // CSV Data
            for (Product p : products) {
                writer.printf("%d,\"%s\",\"%s\",%.2f,%d,%d,%d,%s\n",
                    p.getId(),
                    escapeCsv(p.getName()),
                    escapeCsv(p.getDescription() != null ? p.getDescription() : ""),
                    p.getPrice(),
                    p.getStockQuantity(),
                    p.getMinStock(),
                    p.getGstPercent() != null ? p.getGstPercent() : 0,
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
            duplicate.setPrice(original.getPrice());
            duplicate.setStockQuantity(0);
            duplicate.setMinStock(original.getMinStock());
            duplicate.setGstPercent(original.getGstPercent());
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
        } else {
            Product existingProduct = loadVersionSafeProduct(product.getId());

            if (!existingProduct.getShop().getId().equals(shop.getId())) {
                throw new RuntimeException("Unauthorized access");
            }

            oldProductState = snapshotProduct(existingProduct);

            existingProduct.setName(product.getName());
            existingProduct.setDescription(product.getDescription());
            existingProduct.setPrice(product.getPrice());
            existingProduct.setStockQuantity(product.getStockQuantity());
            existingProduct.setMinStock(product.getMinStock());
            existingProduct.setGstPercent(product.getGstPercent());

            productToSave = existingProduct;
        }
        
        productToSave.setShop(shop);
        productToSave.setActive(true);

        if (productToSave.getMinStock() == null) {
            productToSave.setMinStock(5);
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

    private Map<String, Object> snapshotProduct(Product product) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", product.getId());
        snapshot.put("name", product.getName());
        snapshot.put("description", product.getDescription());
        snapshot.put("price", product.getPrice());
        snapshot.put("stockQuantity", product.getStockQuantity());
        snapshot.put("minStock", product.getMinStock());
        snapshot.put("gstPercent", product.getGstPercent());
        snapshot.put("active", product.isActive());
        snapshot.put("shopId", product.getShop() != null ? product.getShop().getId() : null);
        return snapshot;
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
}
