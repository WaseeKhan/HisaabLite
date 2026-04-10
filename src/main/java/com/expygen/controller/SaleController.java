package com.expygen.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expygen.dto.CartItem;
import com.expygen.dto.ProductLookupResult;
import com.expygen.dto.SaleBatchTraceSummaryDTO;
import com.expygen.dto.SaleHistoryDTO;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.User;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.PdfService;
import com.expygen.service.ProductService;
import com.expygen.service.SaleBatchTraceService;
import com.expygen.service.SaleService;
import com.expygen.service.WhatsAppService;

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
    private final WhatsAppService whatsAppService;
    private final PdfService pdfService;
    private final SaleBatchTraceService saleBatchTraceService;

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow();
    }

    private Sale getAuthorizedSale(Long saleId, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        Sale sale = saleRepository.findByIdWithShop(saleId)
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        if (sale.getShop() == null || user.getShop() == null
                || !sale.getShop().getId().equals(user.getShop().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found");
        }

        return sale;
    }

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

        BigDecimal totalAmount = cart.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("shop", user.getShop());
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "billing");
        PlanType planType = user.getShop().getPlanType();
        String planTypeDisplay = planType != null ? planType.name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);

        log.info("Billing page loaded - Shop: {}, Plan: {}",
                user.getShop().getName(), planTypeDisplay);

        return "sale-form";
    }

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

        int sellableStock = saleService.getSellableStockForProduct(product);

        if (sellableStock <= 0) {
            throw new RuntimeException("Product is out of stock");
        }

        if (quantity > sellableStock) {
            throw new RuntimeException("Only " + sellableStock + " items available");
        }

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        if (cart == null) {
            cart = new ArrayList<>();
        }

        boolean found = false;

        for (CartItem item : cart) {

            if (item.getProductId().equals(productId)) {

                int newQty = item.getQuantity() + quantity;

                if (newQty > sellableStock) {
                    throw new RuntimeException("Not enough stock available");
                }

                BigDecimal price = product.getPrice();
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(newQty));

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

    @GetMapping("/remove/{index}")
    public String removeFromCart(@PathVariable int index, HttpSession session) {
        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
        if (cart != null && index >= 0 && index < cart.size()) {
            cart.remove(index);
            session.setAttribute("cart", cart);
        }
        return "redirect:/sales/new";
    }

    @PostMapping("/complete")
    public String completeSale(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String doctorName,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) Double amountReceived,
            @RequestParam(required = false) Double changeReturned,
            @RequestParam(required = false) BigDecimal discountAmount,
            @RequestParam(required = false) BigDecimal discountPercent,
            @RequestParam(required = false) boolean sendWhatsApp,
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

            savedSale = saleService.completeSale(
                    cart,
                    user.getShop(),
                    user,
                    customerName,
                    customerPhone,
                    paymentMode,
                    amountReceived,
                    changeReturned,
                    discountAmount,
                    discountPercent);

            session.removeAttribute("cart");

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/sales/new";
        }

        if (sendWhatsApp && savedSale != null && savedSale.getCustomerPhone() != null
                && !savedSale.getCustomerPhone().isEmpty()) {
            try {
                boolean sent = whatsAppService.sendInvoice(savedSale, savedSale.getCustomerPhone());
                if (sent) {
                    redirectAttributes.addFlashAttribute("whatsappSuccess", "✅ Invoice sent on WhatsApp!");
                } else {
                    redirectAttributes.addFlashAttribute("whatsappWarning", "⚠️ Sale saved but WhatsApp failed");
                }
            } catch (Exception e) {
                log.error("WhatsApp error during sale completion: {}", e.getMessage());
                redirectAttributes.addFlashAttribute("whatsappError", "WhatsApp sending failed");
            }
        }

        String redirectUrl = "redirect:/sales/new?saved=true&invoiceId=" + savedSale.getId();
        if (savedSale.getCustomerPhone() != null && !savedSale.getCustomerPhone().isBlank()) {
            redirectUrl += "&phone=" + UriUtils.encode(savedSale.getCustomerPhone(), StandardCharsets.UTF_8);
        }

        return redirectUrl;
    }

    @GetMapping("/invoice/{saleId}")
    public String viewInvoice(@PathVariable Long saleId, Model model, Authentication authentication) {

        Sale sale = getAuthorizedSale(saleId, authentication);

        List<SaleItem> items = saleItemRepository.findBySale(sale);

        model.addAttribute("sale", sale);
        model.addAttribute("items", items);
        model.addAttribute("batchTraceBySaleItemId", saleBatchTraceService.getBatchTraceBySaleItem(items));

        return "invoice";
    }

    // ======Thermal Invoice =====//

    @GetMapping("/invoice/{saleId}/thermal")
    public String viewThermalInvoice(@PathVariable Long saleId, Model model, Authentication authentication) {

        Sale sale = getAuthorizedSale(saleId, authentication);

        List<SaleItem> items = saleItemRepository.findBySale(sale);

        model.addAttribute("sale", sale);
        model.addAttribute("items", items);
        model.addAttribute("batchTraceBySaleItemId", saleBatchTraceService.getBatchTraceBySaleItem(items));

        return "invoice-thermal";
    }

    // ===== SALES HISTORY WITH FILTERS =====
    @GetMapping("/history")
    public String salesHistory(
            Model model,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) String sortBy) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        // Default sort by date descending
        Sort sort = Sort.by("saleDate").descending();
        if (sortBy != null && sortBy.equals("amount")) {
            sort = Sort.by("totalAmount").descending();
        } else if (sortBy != null && sortBy.equals("amount_asc")) {
            sort = Sort.by("totalAmount").ascending();
        }

        Pageable pageable = PageRequest.of(page, 10, sort);

        Page<Sale> salesPage;

        // Apply filters
        if (keyword != null && !keyword.trim().isEmpty()) {
            salesPage = saleRepository.searchSales(user.getShop(), keyword.trim(), pageable);
        } else if (status != null && !status.isEmpty()) {
            SaleStatus saleStatus;
            try {
                saleStatus = SaleStatus.valueOf(status);
                salesPage = saleRepository.findByShopAndStatus(user.getShop(), saleStatus, pageable);
            } catch (IllegalArgumentException e) {
                salesPage = saleRepository.findByShop(user.getShop(), pageable);
            }
        } else if (dateRange != null && !dateRange.isEmpty()) {
            LocalDateTime startDate = getStartDate(dateRange);
            salesPage = saleRepository.findByShopAndSaleDateAfter(user.getShop(), startDate, pageable);
        } else {
            salesPage = saleRepository.findByShop(user.getShop(), pageable);
        }

        // Convert to DTO for display
        List<SaleHistoryDTO> saleDTOs = mapSaleHistoryDtos(salesPage.getContent());

        // Add filter attributes to model
        model.addAttribute("sales", saleDTOs);
        model.addAttribute("salesPage", salesPage);
        model.addAttribute("currentPage", "sales-history");
        model.addAttribute("currentKeyword", keyword);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentDateRange", dateRange);
        model.addAttribute("currentSortBy", sortBy);

        model.addAttribute("role", user.getRole().name());
        model.addAttribute("shop", user.getShop());
        model.addAttribute("planType",
                user.getShop().getPlanType() != null ? user.getShop().getPlanType().name() : "FREE");
        model.addAttribute("user", user);

        // Get summary stats
        model.addAttribute("totalSales", saleRepository.countByShop(user.getShop()));
        model.addAttribute("totalRevenue", saleRepository.getTotalRevenueByShop(user.getShop()));
        model.addAttribute("completedCount", saleRepository.countByShopAndStatus(user.getShop(), SaleStatus.COMPLETED));
        model.addAttribute("cancelledCount", saleRepository.countByShopAndStatus(user.getShop(), SaleStatus.CANCELLED));

        return "sales-history";
    }

    // AJAX endpoint for live search
    // AJAX endpoint for live search - FIXED with DTO
    @GetMapping("/history/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchSalesHistory(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateRange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "saleDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow();

            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, 10, sort);

            Page<Sale> salesPage;

            // Apply filters
            if (keyword != null && !keyword.trim().isEmpty()) {
                salesPage = saleRepository.searchSales(user.getShop(), keyword.trim(), pageable);
            } else if (status != null && !status.isEmpty()) {
                SaleStatus saleStatus;
                try {
                    saleStatus = SaleStatus.valueOf(status);
                    salesPage = saleRepository.findByShopAndStatus(user.getShop(), saleStatus, pageable);
                } catch (IllegalArgumentException e) {
                    salesPage = saleRepository.findByShop(user.getShop(), pageable);
                }
            } else if (dateRange != null && !dateRange.isEmpty()) {
                LocalDateTime startDate = getStartDate(dateRange);
                salesPage = saleRepository.findByShopAndSaleDateAfter(user.getShop(), startDate, pageable);
            } else {
                salesPage = saleRepository.findByShop(user.getShop(), pageable);
            }

            // Convert to DTO to avoid circular reference
            List<SaleHistoryDTO> saleDTOs = mapSaleHistoryDtos(salesPage.getContent());

            Map<String, Object> response = new HashMap<>();
            response.put("sales", saleDTOs);
            response.put("currentPage", salesPage.getNumber());
            response.put("totalPages", salesPage.getTotalPages());
            response.put("totalItems", salesPage.getTotalElements());
            response.put("pageSize", salesPage.getSize());
            response.put("hasNext", salesPage.hasNext());
            response.put("hasPrevious", salesPage.hasPrevious());

            log.info("Found {} sales, total pages: {}", saleDTOs.size(), salesPage.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching sales: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // Helper method for date ranges
    private LocalDateTime getStartDate(String dateRange) {
        LocalDateTime now = LocalDateTime.now();
        switch (dateRange) {
            case "today":
                return now.toLocalDate().atStartOfDay();
            case "yesterday":
                return now.minusDays(1).toLocalDate().atStartOfDay();
            case "week":
                return now.minusWeeks(1);
            case "month":
                return now.minusMonths(1);
            case "year":
                return now.minusYears(1);
            default:
                return now.minusMonths(1);
        }
    }

    private List<SaleHistoryDTO> mapSaleHistoryDtos(List<Sale> sales) {
        Map<Long, SaleBatchTraceSummaryDTO> batchSummaryBySaleId = saleBatchTraceService.summarizeSales(sales);
        List<SaleHistoryDTO> saleDTOs = new ArrayList<>();

        for (Sale sale : sales) {
            SaleBatchTraceSummaryDTO batchSummary = batchSummaryBySaleId.get(sale.getId());
            SaleHistoryDTO dto = SaleHistoryDTO.builder()
                    .id(sale.getId())
                    .saleDate(sale.getSaleDate())
                    .totalAmount(sale.getTotalAmount())
                    .customerName(sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in")
                    .customerPhone(sale.getCustomerPhone())
                    .cashierName(sale.getCreatedBy() != null ? sale.getCreatedBy().getName() : "N/A")
                    .status(sale.getStatus() != null ? sale.getStatus().name() : "UNKNOWN")
                    .batchManaged(batchSummary != null && batchSummary.isBatchManaged())
                    .tracedBatchCount(batchSummary != null ? batchSummary.getTracedBatchCount() : 0)
                    .tracedUnits(batchSummary != null ? batchSummary.getTracedUnits() : 0)
                    .nextExpiryDate(batchSummary != null ? batchSummary.getNextExpiryDate() : null)
                    .expiredBatchCount(batchSummary != null ? batchSummary.getExpiredBatchCount() : 0)
                    .build();
            saleDTOs.add(dto);
        }

        return saleDTOs;
    }

    @PostMapping("/cancel/{id}")
    public String cancelSale(@PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getAuthenticatedUser(authentication);
            if (user.getRole() == com.expygen.entity.Role.CASHIER) {
                redirectAttributes.addFlashAttribute("error", "Access denied: cashiers cannot cancel completed sales.");
                return "redirect:/sales/history";
            }
            saleService.cancelSale(id, user);
            redirectAttributes.addFlashAttribute("success", "Sale cancelled successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/sales/history";
    }

    @GetMapping("/search")
    @ResponseBody
    public List<ProductLookupResult> searchProducts(
            @RequestParam String keyword,
            Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        return productService.searchProducts(keyword, user.getShop()).stream()
                .map(product -> ProductLookupResult.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .barcode(product.getBarcode())
                        .genericName(product.getGenericName())
                        .manufacturer(product.getManufacturer())
                        .packSize(product.getPackSize())
                        .price(product.getPrice())
                        .gstPercent(product.getGstPercent())
                        .sellableStock(saleService.getSellableStockForProduct(product))
                        .prescriptionRequired(product.isPrescriptionRequired())
                        .build())
                .toList();
    }

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
    public ResponseEntity<?> updateQuantity(
            @RequestParam int index,
            @RequestParam int change,
            HttpSession session,
            Authentication authentication) {

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
        if (cart == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        if (index < 0 || index >= cart.size()) {
            return ResponseEntity.badRequest().body("Invalid cart item");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        CartItem item = cart.get(index);

        if (change <= -9999) {
            cart.remove(index);
            session.setAttribute("cart", cart);
            return ResponseEntity.ok(cart);
        }

        int newQty = item.getQuantity() + change;
        if (newQty <= 0) {
            cart.remove(index);
            session.setAttribute("cart", cart);
            return ResponseEntity.ok(cart);
        }

        Product product = productService
                .getProductByIdAndShop(item.getProductId(), user.getShop())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        int sellableStock = saleService.getSellableStockForProduct(product);

        if (newQty > sellableStock) {
            return ResponseEntity.badRequest()
                    .body("Only " + sellableStock + " items available");
        }

        BigDecimal price = product.getPrice();
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(newQty));
        Integer gstPercent = product.getGstPercent() != null ? product.getGstPercent() : 0;
        BigDecimal gstMultiplier = BigDecimal.valueOf(gstPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal gstAmount = subtotal.multiply(gstMultiplier);
        BigDecimal totalWithGst = subtotal.add(gstAmount);

        item.setQuantity(newQty);
        item.setPrice(price);
        item.setSubtotal(subtotal);
        item.setGstPercent(gstPercent);
        item.setGstAmount(gstAmount);
        item.setTotalWithGst(totalWithGst);
        session.setAttribute("cart", cart);

        return ResponseEntity.ok(cart);
    }

    @PostMapping("/cancel")
    @ResponseBody
    public ResponseEntity<?> cancelSaleSession(HttpSession session) {
        try {
            session.removeAttribute("cart");
            session.removeAttribute("totalAmount");
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error cancelling sale");
        }
    }

    @PostMapping("/invoice/{saleId}/send-whatsapp-pdf")
    @ResponseBody
    public ResponseEntity<?> sendWhatsAppWithPdf(
            @PathVariable Long saleId,
            @RequestParam String phoneNumber,
            @RequestParam(required = false) MultipartFile file,
            Authentication authentication) {

        try {
            Sale sale = getAuthorizedSale(saleId, authentication);

            boolean sent;

            if (file != null && !file.isEmpty()) {
                sent = whatsAppService.sendInvoiceWithPdfAttachment(sale, phoneNumber, file);
            } else {
                sent = whatsAppService.sendInvoiceWithPdf(sale, phoneNumber);
            }

            if (sent) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Invoice sent successfully"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to send invoice"));
            }

        } catch (Exception e) {
            log.error("Error", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoice/{saleId}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long saleId, Authentication authentication) {
        Sale sale = getAuthorizedSale(saleId, authentication);

        byte[] pdfBytes = pdfService.generateInvoicePdf(sale);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename("invoice_" + saleId + ".pdf").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
