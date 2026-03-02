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
        model.addAttribute("shop", user.getShop());
        model.addAttribute("role", user.getRole().name());

        return "sale-form";
    }

    // 2 Add to cart
    @PostMapping("/add")
    @ResponseBody
    public List<CartItem> addToCart(
            @RequestParam Long productId,
            @RequestParam int quantity,
            HttpSession session,
            Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        Product product = productService
                .getProductByIdAndShop(productId, user.getShop())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // ================= STOCK VALIDATION =================

        if (product.getStockQuantity() <= 0) {
            throw new RuntimeException("Product is out of stock");
        }

        if (quantity > product.getStockQuantity()) {
            throw new RuntimeException("Only " + product.getStockQuantity() + " items available");
        }

        // ====================================================

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        if (cart == null) {
            cart = new ArrayList<>();
        }

        boolean found = false;

        for (CartItem item : cart) {

            if (item.getProductId().equals(productId)) {

                int newQty = item.getQuantity() + quantity;

                // RECHECK STOCK FOR UPDATED QTY
                if (newQty > product.getStockQuantity()) {
                    throw new RuntimeException("Not enough stock available");
                }

                item.setQuantity(newQty);
                item.setSubtotal(
                        product.getPrice().multiply(BigDecimal.valueOf(newQty)));

                found = true;
                break;
            }
        }

        if (!found) {

            CartItem cartItem = new CartItem();
            cartItem.setProductId(product.getId());
            cartItem.setProductName(product.getName());
            cartItem.setPrice(product.getPrice());
            cartItem.setQuantity(quantity);
            cartItem.setSubtotal(
                    product.getPrice().multiply(BigDecimal.valueOf(quantity)));

            cart.add(cartItem);
        }

        session.setAttribute("cart", cart);

        return cart;
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
            return "redirect:/sales/new"; // 🔥 FIXED
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        Sale savedSale = null;

        try {

            // 🔹 EXISTING LOGIC (UNCHANGED)
            savedSale = saleService.completeSale(cart, user.getShop(), user);

            // ==========================
            // DISCOUNT LOGIC START
            // ==========================

            if (discountAmount == null)
                discountAmount = BigDecimal.ZERO;
            if (discountPercent == null)
                discountPercent = BigDecimal.ZERO;

            BigDecimal originalTotal = savedSale.getTotalAmount();

            if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {

                if (discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new RuntimeException("Discount % cannot exceed 100 %");
                }

                discountAmount = originalTotal
                        .multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100));
            }

            if (discountAmount.compareTo(originalTotal) > 0) {
                throw new RuntimeException("Discount cannot exceed total amount");
            }

            BigDecimal finalTotal = originalTotal.subtract(discountAmount);

            // FIX: use savedSale instead of sale
            savedSale.setDiscountAmount(discountAmount);
            savedSale.setDiscountPercent(discountPercent);
            savedSale.setTotalAmount(finalTotal);

            // ==========================
            // DISCOUNT LOGIC END
            // ==========================

            // 🔹 Customer + payment logic
            savedSale.setCustomerName(customerName);
            savedSale.setCustomerPhone(customerPhone);
            savedSale.setPaymentMode(paymentMode);
            savedSale.setAmountReceived(
                    amountReceived != null ? amountReceived : 0.0);

            savedSale.setChangeReturned(
                    changeReturned != null ? changeReturned : 0.0);

            saleRepository.save(savedSale);

            session.removeAttribute("cart");

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/sales/new";
        }

        // SUCCESS REDIRECT WITH INVOICE ID
        return "redirect:/sales/new?saved=true&invoiceId=" + savedSale.getId();
    }

    // complete sale end here

    // generate invoice

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

    // Sales History
    @GetMapping("/history")
    public String salesHistory(Model model,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        Pageable pageable = PageRequest.of(page, 5, Sort.by("saleDate").descending());

        Page<Sale> salesPage = saleRepository.findByShop(user.getShop(), pageable);

        model.addAttribute("salesPage", salesPage);
        model.addAttribute("currentPage", page);

        return "sales-history";
    }

    // cancel sale

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

    // cart live update

    @RequestMapping("/cart")
    @ResponseBody
    public List<CartItem> getCart(HttpSession session) {

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        if (cart == null) {
            return new ArrayList<>();
        }

        return cart;
    }



    @PostMapping("/update-qty")
@ResponseBody
public List<CartItem> updateQuantity(
        @RequestParam int index,
        @RequestParam int change,
        HttpSession session) {

    List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

    if (cart != null && index >= 0 && index < cart.size()) {

        CartItem item = cart.get(index);

        int newQty = item.getQuantity() + change;

        // Agar quantity 0 ya negative ho gayi to remove
        if (newQty <= 0) {
            cart.remove(index);
        } else {
            item.setQuantity(newQty);
        }

        session.setAttribute("cart", cart);
    }

    return cart;
}
}