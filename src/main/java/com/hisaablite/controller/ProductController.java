package com.hisaablite.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // LIST PRODUCTS

    @GetMapping
    public String listProducts(Model model, Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        model.addAttribute("products",
                productRepository.findByShopAndActiveTrue(user.getShop()));
                model.addAttribute("role", user.getRole().name());

        return "products";
    }

    // NEW PRODUCT FORM

    @GetMapping("/new")
    public String newProductForm(Model model, Authentication authentication) {
        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        model.addAttribute("product", new Product());
        model.addAttribute("role", user.getRole().name());
        return "product-form";
    }

    // SAVE OR UPDATE PRODUCT

    @PostMapping("/save")
    public String saveOrUpdateProduct(@ModelAttribute Product product,
            Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();
        product.setShop(user.getShop());
        product.setActive(true);

        // If minStock null and default 5
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

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Unauthorized access");
        }

        model.addAttribute("product", product);
          model.addAttribute("role", user.getRole().name());
        return "product-form";
    }

    // DELETE PRODUCT (SOFT DELETE)

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
        product.setActive(false);
        productRepository.save(product);
        return "redirect:/products";
    }
}