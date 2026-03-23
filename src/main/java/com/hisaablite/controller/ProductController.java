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

import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.ProductService;
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

        // Sort by id descending (latest first)
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
        Page<Product> productPage;

        if (keyword != null && !keyword.trim().isEmpty()) {
            productPage = productService.searchProductsWithPagination(keyword.trim(), user.getShop(), pageable);
            model.addAttribute("keyword", keyword);
        } else {
            productPage = productRepository.findByShopAndActiveTrue(user.getShop(), pageable);
        }

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", productPage.getNumber());
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalItems", productPage.getTotalElements());
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        model.addAttribute("user", user);
        model.addAttribute("shop", shop);

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

    // DUPLICATE PRODUCT
    @PostMapping("/duplicate")
    @ResponseBody
    public ResponseEntity<Map<String, String>> duplicateProduct(
            @RequestParam Long id,
            Authentication authentication) {
        
        try {
            User user = getUser(authentication);
            Product original = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Check authorization
            if (!original.getShop().getId().equals(user.getShop().getId())) {
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
            duplicate.setShop(user.getShop());
            duplicate.setActive(true);

            productRepository.save(duplicate);
            
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
                    product.setActive(false);
                    productRepository.save(product);
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

    // NEW PRODUCT FORM
    @GetMapping("/new")
    public String newProductForm(Model model, Authentication authentication) {
        User user = getUser(authentication);
        model.addAttribute("product", new Product());
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        return "product-form";
    }

    // SAVE OR UPDATE PRODUCT
    @PostMapping("/save")
    public String saveOrUpdateProduct(@ModelAttribute Product product,
            Authentication authentication) {

        User user = getUser(authentication);
        product.setShop(user.getShop());
        product.setActive(true);

        if (product.getMinStock() == null) {
            product.setMinStock(5);
        }

        productRepository.save(product);

        return "redirect:/products";
    }

    // EDIT PRODUCT
    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id,
            Model model,
            Authentication authentication) {

        User user = getUser(authentication);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }

        model.addAttribute("product", product);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "products");
        return "product-form";
    }

    // DELETE PRODUCT (SOFT DELETE)
    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id,
            Authentication authentication) {

        User user = getUser(authentication);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }
        product.setActive(false);
        productRepository.save(product);
        return "redirect:/products";
    }
}