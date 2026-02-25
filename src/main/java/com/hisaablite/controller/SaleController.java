package com.hisaablite.controller;

import com.hisaablite.dto.CartItem;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.User;
import com.hisaablite.service.ProductService;
import com.hisaablite.service.SaleService;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
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
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;

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

    // Compute total amount
    BigDecimal totalAmount = cart.stream()
            .map(CartItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    model.addAttribute("totalAmount", totalAmount);

    return "sale-form"; 
    }


    // 2 Add to cart
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

    // 3 Remove from cart
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

        // Flash attribute for success
        redirectAttributes.addFlashAttribute("success", "Sale completed successfully!");
        } catch (RuntimeException e) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/sales/new";
    }


    //generate invoice

    @GetMapping("/invoice/{saleId}")
    public String viewInvoice(@PathVariable Long saleId, Model model) {

    // Fetch sale with shop to avoid lazy fetch errors
    Sale sale = saleRepository.findByIdWithShop(saleId)
            .orElseThrow(() -> new RuntimeException("Sale not found"));

    // Fetch items for the sale
    List<SaleItem> items = saleItemRepository.findBySale(sale);

    model.addAttribute("sale", sale);
    model.addAttribute("items", items);

    return "invoice"; // templates/invoice.html render hoga
    }
}