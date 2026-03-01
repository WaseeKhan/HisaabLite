package com.hisaablite.controller;

import com.hisaablite.dto.CartItem;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.SaleStatus;
import com.hisaablite.entity.User;
import com.hisaablite.service.ProductService;
import com.hisaablite.service.SaleService;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;


import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public String completeSale(
        @RequestParam(required = false) String customerName,
        @RequestParam(required = false) String customerPhone,
        @RequestParam(required = false) String paymentMode,
        @RequestParam(required = false) Double amountReceived,
        @RequestParam(required = false) Double changeReturned,
        @RequestParam(required = false) BigDecimal discountAmount,
        @RequestParam(required = false) BigDecimal discountPercent,
        HttpSession session,
        Authentication authentication,
        RedirectAttributes redirectAttributes) {

    List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

    if (cart == null || cart.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", "Cart is empty!");
        return "redirect:/sales/new";
    }

    User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow();

    try {

        // 🔹 EXISTING LOGIC (DO NOT TOUCH)
        Sale sale = saleService.completeSale(cart, user.getShop(), user);

        // ==========================
        // DISCOUNT LOGIC START
        // ==========================

        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (discountPercent == null) discountPercent = BigDecimal.ZERO;

        BigDecimal originalTotal = sale.getTotalAmount();

        // If percentage entered → calculate amount
        if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {

            if (discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                redirectAttributes.addFlashAttribute("error", "Discount % cannot exceed 100");
                return "redirect:/sales/new";
            }

            discountAmount = originalTotal
                    .multiply(discountPercent)
                    .divide(BigDecimal.valueOf(100));
        }

        // Prevent over-discount
        if (discountAmount.compareTo(originalTotal) > 0) {
            redirectAttributes.addFlashAttribute("error", "Discount cannot exceed total amount");
            return "redirect:/sales/new";
        }

        BigDecimal finalTotal = originalTotal.subtract(discountAmount);

        // Save discount in Sale entity (if fields exist)
        sale.setDiscountAmount(discountAmount);
        sale.setDiscountPercent(discountPercent);
        sale.setTotalAmount(finalTotal);

        // ==========================
        // DISCOUNT LOGIC END
        // ==========================


        // 🔹 Existing customer + payment logic
        sale.setCustomerName(customerName);
        sale.setCustomerPhone(customerPhone);
        sale.setPaymentMode(paymentMode);
        sale.setAmountReceived(amountReceived);
        sale.setChangeReturned(changeReturned);

        saleRepository.save(sale);

        session.removeAttribute("cart");

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

    //Sales History
    @GetMapping("/history")
    public String salesHistory(Model model,
                           Authentication authentication,
                           @RequestParam(defaultValue = "0") int page) {

    User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow();

   Pageable pageable = PageRequest.of(page, 5, Sort.by("saleDate").descending());

    Page<Sale> salesPage =
        saleRepository.findByShop(user.getShop(), pageable);
            

    model.addAttribute("salesPage", salesPage);
    model.addAttribute("currentPage", page);

    return "sales-history";
    }



//cancel sale 

    @GetMapping("/cancel/{id}")
    public String cancelSale(@PathVariable Long id,
                         RedirectAttributes redirectAttributes) {

    try {
        saleService.cancelSale(id);
        redirectAttributes.addFlashAttribute("success", "Sale cancelled successfully!");
    } catch (RuntimeException e) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    }

    return "redirect:/sales/history";
}



@GetMapping("/search")
@ResponseBody
public List<Product> searchProducts(
        @RequestParam String keyword,
        Authentication authentication) {

    User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow();

    return productService.searchProducts(keyword, user.getShop());
}


}