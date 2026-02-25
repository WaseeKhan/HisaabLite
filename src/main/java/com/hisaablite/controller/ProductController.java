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

    @GetMapping
    public String listProducts(Model model, Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        model.addAttribute("products",
                productRepository.findByShop(user.getShop()));

        return "products";
    }

    @GetMapping("/new")
    public String newProductForm() {
        return "product-form";
    }

    @PostMapping
    public String saveProduct(@RequestParam String name,
                              @RequestParam BigDecimal price,
                              @RequestParam Integer stockQuantity,
                              Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Product product = Product.builder()
                .name(name)
                .price(price)
                .stockQuantity(stockQuantity)
                .shop(user.getShop())
                .active(true)
                .build();

        productRepository.save(product);

        return "redirect:/products";
    }
}