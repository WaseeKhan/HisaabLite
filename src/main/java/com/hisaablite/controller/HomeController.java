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
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {


        

    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final SaleService saleService;   // IMPORTANT



    @GetMapping("/app")
    public String ultraDashboard(Model model, Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Shop shop = user.getShop();

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

        model.addAttribute("todayRevenue", todayRevenue != null ? todayRevenue : 0);
        model.addAttribute("todayInvoices", todayInvoices != null ? todayInvoices : 0);
        model.addAttribute("todayItems", todayItems != null ? todayItems : 0);

        // =======================
        // 7 DAY CHART DATA
        // =======================

        Map<String, Object> chartData = saleService.getLast7DaysChartData(shop);

        model.addAttribute("labels", chartData.get("labels"));
        model.addAttribute("revenues", chartData.get("revenues"));

        // =======================
        // LOW STOCK
        // =======================

        List<Product> lowStockProducts =
                productRepository.findLowStockProducts(shop);

        model.addAttribute("lowStockProducts", lowStockProducts);

        return "ultra-dashboard";  // make sure file name matches
    }
}