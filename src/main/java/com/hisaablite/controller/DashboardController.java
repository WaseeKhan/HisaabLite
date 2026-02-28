package com.hisaablite.controller;



import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.SaleService;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class DashboardController {

    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final SaleService saleService;

    // ==============================
    // ROLE BASED REDIRECT
    // ==============================
    @GetMapping("/dashboard")
    public String dashboardRedirect(Authentication auth) {

        String role = auth.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if (role.equals("ROLE_OWNER")) {
            return "redirect:/owner/dashboard";
        } else if (role.equals("ROLE_MANAGER")) {
            return "redirect:/manager/dashboard";
        } else {
            return "redirect:/cashier/dashboard";
        }
    }

    // ==============================
    // OWNER DASHBOARD
    // ==============================
    @GetMapping("/owner/dashboard")
    public String ownerDashboard(Model model, Authentication authentication) {
        return loadDashboard(model, authentication, "OWNER");
    }

    // ==============================
    // MANAGER DASHBOARD
    // ==============================
    @GetMapping("/manager/dashboard")
    public String managerDashboard(Model model, Authentication authentication) {
        return loadDashboard(model, authentication, "MANAGER");
    }

    // ==============================
    // CASHIER DASHBOARD
    // ==============================
    @GetMapping("/cashier/dashboard")
    public String cashierDashboard(Model model, Authentication authentication) {
        return loadDashboard(model, authentication, "CASHIER");
    }

    // ==============================
    // COMMON DASHBOARD LOGIC
    // ==============================
    private String loadDashboard(Model model,
                                 Authentication authentication,
                                 String role) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Shop shop = user.getShop();

        model.addAttribute("shop", shop);
        model.addAttribute("role", role);

        // =======================
        // TODAY DATA
        // =======================

        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = today.atTime(23, 59, 59);

        Double todayRevenue = saleRepository
                .getTodayTotalRevenue(shop, startToday, endToday);

        Long todayInvoices = saleRepository
                .getTodayInvoiceCount(shop, startToday, endToday);

        Long todayItems = saleItemRepository
                .getTodayItemsSold(shop, startToday, endToday);

        model.addAttribute("todayRevenue",
                todayRevenue != null ? todayRevenue : 0);

        model.addAttribute("todayInvoices",
                todayInvoices != null ? todayInvoices : 0);

        model.addAttribute("todayItems",
                todayItems != null ? todayItems : 0);

        // =======================
        // 7 DAY CHART DATA
        // =======================

        Map<String, Object> chartData =
                saleService.getLast7DaysChartData(shop);

        model.addAttribute("labels", chartData.get("labels"));
        model.addAttribute("revenues", chartData.get("revenues"));

        // =======================
        // LOW STOCK
        // =======================

        List<Product> lowStockProducts =
                productRepository.findLowStockProducts(shop);

        model.addAttribute("lowStockProducts", lowStockProducts);

        Long totalStaff = userRepository.countByShop(shop);
        model.addAttribute("totalStaff", totalStaff);

        return "ultra-dashboard";
    }

    // ==============================
    // LIVE METRICS (AJAX)
    // ==============================
    @GetMapping("/app/metrics")
    @ResponseBody
    public Map<String, Object> getLiveMetrics(
            Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Shop shop = user.getShop();

        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = today.atTime(23, 59, 59);

        Map<String, Object> data = new HashMap<>();

        data.put("todayRevenue",
                saleRepository.getTodayTotalRevenue(shop,
                        startToday, endToday));

        data.put("todayInvoices",
                saleRepository.getTodayInvoiceCount(shop,
                        startToday, endToday));

        data.put("todayItems",
                saleItemRepository.getTodayItemsSold(shop,
                        startToday, endToday));

        data.put("totalStaff",
                userRepository.countByShop(shop));

        return data;
    }
}