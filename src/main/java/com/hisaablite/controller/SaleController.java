package com.hisaablite.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hisaablite.dto.CartItem;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.User;
import com.hisaablite.entity.PlanType;  
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.ProductService;
import com.hisaablite.service.SaleService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;  

@Controller
@RequiredArgsConstructor
@RequestMapping("/sales")
@Slf4j 
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

        // calculate total amount here
        BigDecimal totalAmount = cart.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("shop", user.getShop());
        model.addAttribute("role", user.getRole().name());
        
     
        PlanType planType = user.getShop().getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);
        
        log.info("Billing page loaded - Shop: {}, Plan: {}", 
                user.getShop().getName(), planTypeDisplay);

        return "sale-form";
    }

    // Add to cart start here
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

        if (product.getStockQuantity() <= 0) {
            throw new RuntimeException("Product is out of stock");
        }

        if (quantity > product.getStockQuantity()) {
            throw new RuntimeException("Only " + product.getStockQuantity() + " items available");
        }

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        if (cart == null) {
            cart = new ArrayList<>();
        }

        boolean found = false;

        for (CartItem item : cart) {

            if (item.getProductId().equals(productId)) {

                int newQty = item.getQuantity() + quantity;

           
                if (newQty > product.getStockQuantity()) {
                    throw new RuntimeException("Not enough stock available");
                }

                BigDecimal price = product.getPrice();
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(newQty));

                // GST calculation
                Integer gstPercent = product.getGstPercent() != null ? product.getGstPercent() : 0;
                BigDecimal gstMultiplier = BigDecimal.valueOf(gstPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal gstAmount = subtotal.multiply(gstMultiplier);
                BigDecimal totalWithGst = subtotal.add(gstAmount);

                item.setQuantity(newQty);
                item.setSubtotal(subtotal);
                item.setGstPercent(gstPercent);
                item.setGstAmount(gstAmount);
                item.setTotalWithGst(totalWithGst);

                found = true;
                break;
            }
        }

        if (!found) {

            BigDecimal price = product.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));

            // GST calculation
            Integer gstPercent = product.getGstPercent() != null ? product.getGstPercent() : 0;
            BigDecimal gstMultiplier = BigDecimal.valueOf(gstPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal gstAmount = subtotal.multiply(gstMultiplier);
            BigDecimal totalWithGst = subtotal.add(gstAmount);

            CartItem cartItem = CartItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .price(price)
                    .quantity(quantity)
                    .subtotal(subtotal)
                    .gstPercent(gstPercent)
                    .gstAmount(gstAmount)
                    .totalWithGst(totalWithGst)
                    .build();

            cart.add(cartItem);
        }

        session.setAttribute("cart", cart);

        return cart;
    }

    // add to cart end here

    // Remove from cart
    @GetMapping("/remove/{index}")
    public String removeFromCart(@PathVariable int index, HttpSession session) {
        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
        if (cart != null && index >= 0 && index < cart.size()) {
            cart.remove(index);
            session.setAttribute("cart", cart);
        }
        return "redirect:/sales/new";
    }

    // Complete sale
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

        Sale savedSale = null;

        try {

            savedSale = saleService.completeSale(cart, user.getShop(), user);

            // DISCOUNT LOGIC START

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
            savedSale.setDiscountAmount(discountAmount);
            savedSale.setDiscountPercent(discountPercent);
            savedSale.setTotalAmount(finalTotal);

            // DISCOUNT LOGIC END

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

        return "invoice";
    }

    // Sales History
    @GetMapping("/history")
    public String salesHistory(Model model,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        Pageable pageable = PageRequest.of(page, 10, Sort.by("saleDate").descending());

        Page<Sale> salesPage = saleRepository.findByShop(user.getShop(), pageable);

        model.addAttribute("salesPage", salesPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("role", user.getRole().name());
       
        PlanType planType = user.getShop().getPlanType();
        model.addAttribute("planType", planType != null ? planType.name() : "FREE");

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

    @PostMapping("/cancel")
    @ResponseBody
    public ResponseEntity<?> cancelSale(HttpSession session) {
        try {
            // Clear cart from session
            session.removeAttribute("cart");
            session.removeAttribute("totalAmount");

            // Return empty cart as JSON
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error cancelling sale");
        }
    }
}