package com.hisaablite.controller;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ===============================
    // LIST PRODUCTS
    // ===============================
    @GetMapping
    public String listProducts(Model model, Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        model.addAttribute("products",
                productRepository.findByShopAndActiveTrue(user.getShop()));

        return "products";
    }

    // ===============================
    // NEW PRODUCT FORM
    // ===============================
    @GetMapping("/new")
    public String newProductForm(Model model) {

        model.addAttribute("product", new Product());
        return "product-form";
    }

    // ===============================
    // SAVE OR UPDATE PRODUCT
    // ===============================
    @PostMapping("/save")
    public String saveOrUpdateProduct(@ModelAttribute Product product,
                                      Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        // Always enforce shop & active
        product.setShop(user.getShop());
        product.setActive(true);

        // If minStock null → default 5
        if (product.getMinStock() == null) {
            product.setMinStock(5);
        }

        productRepository.save(product);

        return "redirect:/products";
    }

    // ===============================
    // EDIT PRODUCT
    // ===============================
    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id,
                              Model model,
                              Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Security check: prevent cross-shop access
        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }

        model.addAttribute("product", product);
        return "product-form";
    }

    // ===============================
    // DELETE PRODUCT (SOFT DELETE)
    // ===============================
    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id,
                                Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }

        // Soft delete instead of hard delete
        product.setActive(false);
        productRepository.save(product);

        return "redirect:/products";
    }
}