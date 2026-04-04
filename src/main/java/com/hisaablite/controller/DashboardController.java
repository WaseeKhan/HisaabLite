package com.hisaablite.controller;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.SaleStatus;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.SaleItemRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.PlanLimitService;
import com.hisaablite.service.SaleService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.hisaablite.service.DashboardService;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping
public class DashboardController {

    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final SaleService saleService;
    private final PlanLimitService planLimitService;
    private final DashboardService dashboardService;

    // CANONICAL DASHBOARD
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return loadDashboard(model, authentication, user.getRole().name());
    }

    // LEGACY ROLE-SPECIFIC URLS
    @GetMapping("/owner/dashboard")
    public String ownerDashboard() {
        return "redirect:/dashboard";
    }

    @GetMapping("/manager/dashboard")
    public String managerDashboard() {
        return "redirect:/dashboard";
    }

    @GetMapping("/cashier/dashboard")
    public String cashierDashboard() {
        return "redirect:/dashboard";
    }

    // COMMON DASHBOARD LOGIC
    private String loadDashboard(Model model,
            Authentication authentication,
            String role) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Shop shop = user.getShop();

        // SUBSCRIPTION CHECK
        if (!user.isApproved()) {
            return "redirect:/subscription-required";
        }

        log.info("Loading dashboard for user: {}, shop: {}", user.getUsername(), shop.getName());
        log.info("Shop plan type: {}", shop.getPlanType());

        model.addAttribute("shop", shop);
        model.addAttribute("role", role);

        String planTypeDisplay = shop.getPlanType() != null ? shop.getPlanType().name() : "FREE";
        model.addAttribute("planType", planTypeDisplay);
        model.addAttribute("user", user);
        log.info("Plan type display: {}", planTypeDisplay);

        // ===== TODAY'S DATA =====
        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = today.atTime(23, 59, 59);

        // Today's Revenue (Total of all sales)
        Double todayRevenue = saleRepository.getTodayTotalRevenue(shop, startToday, endToday);
        model.addAttribute("todayRevenue", todayRevenue != null ? todayRevenue : 0);

        // Today's Invoices Count
        Long todayInvoices = saleRepository.getTodayInvoiceCount(shop, startToday, endToday);
        model.addAttribute("todayInvoices", todayInvoices != null ? todayInvoices : 0);

        // Today's Items Sold
        Long todayItems = saleItemRepository.getTodayItemsSold(shop, startToday, endToday);
        model.addAttribute("todayItems", todayItems != null ? todayItems : 0);

        // Completed Revenue (Successful sales)
        Double completedRevenue = saleRepository.getTodayCompletedRevenue(shop, startToday, endToday);
        model.addAttribute("completedRevenue", completedRevenue != null ? completedRevenue : 0);

        // Cancelled Amount
        Double cancelledAmount = saleRepository.getTodayCancelledAmount(shop, startToday, endToday);
        model.addAttribute("cancelledAmount", cancelledAmount != null ? cancelledAmount : 0);

        // Net Revenue
        Double netRevenue = Math.max(0, (completedRevenue != null ? completedRevenue : 0) - (cancelledAmount != null ? cancelledAmount : 0));
        model.addAttribute("netRevenue", netRevenue);

        // Returned Items
        Long returnedItems = saleRepository.getTodayReturnedItems(shop, startToday, endToday);
        model.addAttribute("returnedItems", returnedItems != null ? returnedItems : 0);

        // Total Staff
        Long totalStaff = userRepository.countByShop(shop);
        model.addAttribute("totalStaff", totalStaff);

        // Total Sales Count for today
        Long totalSalesCount = saleRepository.getTodaySalesCount(shop, startToday, endToday);
        model.addAttribute("totalSales", totalSalesCount != null ? totalSalesCount : 0);

        // Completed Count for today
        Long completedCount = saleRepository.getTodayCompletedCount(shop, startToday, endToday);
        model.addAttribute("completedCount", completedCount != null ? completedCount : 0);

        // Unique Customers today
        Long uniqueCustomers = saleRepository.getTodayUniqueCustomers(shop, startToday, endToday);
        model.addAttribute("uniqueCustomers", uniqueCustomers != null ? uniqueCustomers : 0);

        // ===== LIFETIME BUSINESS DATA =====
        // Total Revenue (Completed sales only)
        BigDecimal totalRevenue = saleRepository.getTotalRevenueByShop(shop);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue.doubleValue() : 0);

        // Total Invoices (All completed sales)
        Long totalInvoicesLifetime = saleRepository.countByShopAndStatus(shop, SaleStatus.COMPLETED);
        model.addAttribute("totalInvoicesLifetime", totalInvoicesLifetime != null ? totalInvoicesLifetime : 0);

        // Total Items Sold (Lifetime)
        Long totalItemsSold = saleItemRepository.getTotalItemsSoldByShop(shop);
        model.addAttribute("totalItemsSold", totalItemsSold != null ? totalItemsSold : 0);

        // Total Unique Customers (Lifetime)
        Long totalCustomers = saleRepository.countDistinctCustomersByShop(shop);
        model.addAttribute("totalCustomers", totalCustomers != null ? totalCustomers : 0);

        // 7 DAY CHART DATA
        Map<String, Object> chartData = saleService.getLast7DaysChartData(shop);
        model.addAttribute("labels", chartData.get("labels"));
        model.addAttribute("revenues", chartData.get("revenues"));

        // LOW STOCK
        List<Product> lowStockProducts = productRepository.findLowStockProducts(shop);
        model.addAttribute("lowStockProducts", lowStockProducts);

        model.addAttribute("currentPage", "dashboard");

        // Get Top Selling Products
        List<Map<String, Object>> topProducts = dashboardService.getTopSellingProducts(shop, 5);
        model.addAttribute("topProducts", topProducts);

        // Get Top Customers
        List<Map<String, Object>> topCustomers = dashboardService.getTopCustomers(shop, 5);
        model.addAttribute("topCustomers", topCustomers);

        // Get Recent Activities
        List<Map<String, Object>> recentActivities = dashboardService.getRecentActivities(shop, 10);
        log.info("Recent activities count: {}", recentActivities.size());
        if (!recentActivities.isEmpty()) {
            log.info("First activity: {}", recentActivities.get(0));
        }
        model.addAttribute("recentActivities", recentActivities);

        return "ultra-dashboard";
    }

    @GetMapping("/app/metrics")
    @ResponseBody
    public Map<String, Object> getLiveMetrics(Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Shop shop = user.getShop();
        if (!user.isApproved()) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "subscription_required");
            return response;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = today.atTime(23, 59, 59);

        Map<String, Object> data = new HashMap<>();

        // ===== TODAY'S DATA =====
        Double todayRevenue = saleRepository.getTodayTotalRevenue(shop, startToday, endToday);
        data.put("todayRevenue", todayRevenue != null ? todayRevenue : 0);
        
        Long todayInvoices = saleRepository.getTodayInvoiceCount(shop, startToday, endToday);
        data.put("todayInvoices", todayInvoices != null ? todayInvoices : 0);
        
        Long todayItems = saleItemRepository.getTodayItemsSold(shop, startToday, endToday);
        data.put("todayItems", todayItems != null ? todayItems : 0);
        
        Double completedRevenue = saleRepository.getTodayCompletedRevenue(shop, startToday, endToday);
        data.put("completedRevenue", completedRevenue != null ? completedRevenue : 0);

        Double cancelledAmount = saleRepository.getTodayCancelledAmount(shop, startToday, endToday);
        data.put("cancelledAmount", cancelledAmount != null ? cancelledAmount : 0);

        Double netRevenue = Math.max(0, (completedRevenue != null ? completedRevenue : 0) - (cancelledAmount != null ? cancelledAmount : 0));
        data.put("netRevenue", netRevenue);
        
        Long returnedItems = saleRepository.getTodayReturnedItems(shop, startToday, endToday);
        data.put("returnedItems", returnedItems != null ? returnedItems : 0);
        
        Long totalStaff = userRepository.countByShop(shop);
        data.put("totalStaff", totalStaff);
        
        Long totalSalesCount = saleRepository.getTodaySalesCount(shop, startToday, endToday);
        data.put("todaySalesCount", totalSalesCount != null ? totalSalesCount : 0);
        
        Long completedCount = saleRepository.getTodayCompletedCount(shop, startToday, endToday);
        data.put("completedCount", completedCount != null ? completedCount : 0);
        
        Long uniqueCustomers = saleRepository.getTodayUniqueCustomers(shop, startToday, endToday);
        data.put("uniqueCustomers", uniqueCustomers != null ? uniqueCustomers : 0);

        // ===== LIFETIME DATA =====
        BigDecimal totalRevenue = saleRepository.getTotalRevenueByShop(shop);
        data.put("totalRevenue", totalRevenue != null ? totalRevenue.doubleValue() : 0);
        
        Long totalInvoicesLifetime = saleRepository.countByShopAndStatus(shop, SaleStatus.COMPLETED);
        data.put("totalInvoicesLifetime", totalInvoicesLifetime != null ? totalInvoicesLifetime : 0);
        
        Long totalItemsSold = saleItemRepository.getTotalItemsSoldByShop(shop);
        data.put("totalItemsSold", totalItemsSold != null ? totalItemsSold : 0);
        
        Long totalCustomers = saleRepository.countDistinctCustomersByShop(shop);
        data.put("totalCustomers", totalCustomers != null ? totalCustomers : 0);

        data.put("planType", shop.getPlanType() != null ? shop.getPlanType().name() : "FREE");

        return data;
    }

    @GetMapping("/subscription-required")
    public String subscriptionRequired(Model model, Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        model.addAttribute("plan", user.getShop().getPlanType());

        return "subscription-required";
    }


    // Add this method to your base controller or each dashboard controller
@ModelAttribute
public void addSessionAttributes(Model model, HttpServletRequest request, Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
        String sessionId = request.getSession().getId();
        model.addAttribute("sessionId", sessionId);
    }
}

}
