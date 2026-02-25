package com.hisaablite.controller;

import com.hisaablite.dto.CartItem;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.User;
import com.hisaablite.service.ProductService;
import com.hisaablite.service.SaleService;
import com.hisaablite.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/sales")
public class SaleController {

    private final ProductService productService;
    private final SaleService saleService;
    private final UserRepository userRepository;

    @GetMapping("/new")
    public String newSale(Model model, HttpSession session, Authentication authentication) {

    User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow();

    List<Product> products = productService.getProductsByShop(user.getShop());
    model.addAttribute("products", products);

    List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
    if (cart == null) {
        cart = new ArrayList<>();
        session.setAttribute("cart", cart);
    }
    model.addAttribute("cart", cart);

    // ✅ Compute total amount
    BigDecimal totalAmount = cart.stream()
            .map(CartItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    model.addAttribute("totalAmount", totalAmount);

    return "sale-form";
}


    // 2️⃣ Add to cart
    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam Integer quantity,
                            HttpSession session) {

        Product product = productService.getProductById(productId);

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
        if (cart == null) {
            cart = new ArrayList<>();
        }

        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        CartItem item = CartItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(quantity)
                .subtotal(subtotal)
                .build();

        cart.add(item);
        session.setAttribute("cart", cart);

        return "redirect:/sales/new";
    }

    // 3️⃣ Remove from cart
    @GetMapping("/remove/{index}")
    public String removeFromCart(@PathVariable int index, HttpSession session) {
        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
        if (cart != null && index >= 0 && index < cart.size()) {
            cart.remove(index);
            session.setAttribute("cart", cart);
        }
        return "redirect:/sales/new";
    }

    // 4️ Complete sale

    @PostMapping("/complete")
    public String completeSale(HttpSession session, Authentication authentication, RedirectAttributes redirectAttributes) {
    List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
        if (cart == null || cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Cart is empty!");
            return "redirect:/sales/new";
        }

    User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow();

    try {
        // Transactional save + stock deduction
        saleService.completeSale(cart, user.getShop(), user);

        // Clear cart after sale
        session.removeAttribute("cart");

        // ✅ Flash attribute for success
        redirectAttributes.addFlashAttribute("success", "Sale completed successfully!");
        } catch (RuntimeException e) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/sales/new";
    }
}