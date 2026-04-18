package com.expygen.insights.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expygen.admin.service.AuditService;
import com.expygen.entity.Sale;
import com.expygen.entity.Shop;
import com.expygen.entity.StockAdjustment;
import com.expygen.entity.User;
import com.expygen.insights.dto.CurrentStockRowDto;
import com.expygen.insights.dto.DeadStockRowDto;
import com.expygen.insights.dto.ExpiryLossRowDto;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.NearExpiryRowDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.insights.dto.PurchaseSummaryRowDto;
import com.expygen.insights.dto.SalesSummaryPageDto;
import com.expygen.insights.dto.StockAdjustmentRowDto;
import com.expygen.insights.dto.ActivityAuditRowDto;
import com.expygen.insights.service.AuditInsightsService;
import com.expygen.insights.service.DataImportService;
import com.expygen.insights.service.DeadStockInsightsService;
import com.expygen.insights.service.ExpiryLossInsightsService;
import com.expygen.insights.service.FastSlowMovementInsightsService;
import com.expygen.insights.service.InventoryInsightsService;
import com.expygen.insights.service.ProfitInsightsService;
import com.expygen.insights.service.PurchaseInsightsService;
import com.expygen.insights.service.SalesInsightsService;
import com.expygen.repository.UserRepository;
import com.expygen.repository.StockAdjustmentRepository;
import com.expygen.repository.ProductRepository;
import com.expygen.service.ShopService;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Controller
@RequestMapping("/insights")
public class InsightsController {
    private static final int INSIGHTS_PAGE_SIZE = 10;

    private final SalesInsightsService salesInsightsService;
    private final PurchaseInsightsService purchaseInsightsService;
    private final InventoryInsightsService inventoryInsightsService;
    private final DeadStockInsightsService deadStockInsightsService;
    private final ExpiryLossInsightsService expiryLossInsightsService;
    private final FastSlowMovementInsightsService fastSlowMovementInsightsService;
    private final ProfitInsightsService profitInsightsService;
    private final AuditInsightsService auditInsightsService;
    private final DataImportService dataImportService;
    private final AuditService auditService;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ShopService shopService;

    @ModelAttribute
    public void addInsightsActor(Model model, Principal principal) {
        if (principal == null) {
            return;
        }
        userRepository.findByUsername(principal.getName())
                .ifPresent(user -> model.addAttribute("insightsUser", user));
    }

    @GetMapping
    public String insightsHome(Model model, Principal principal) {
        Shop currentShop = currentShop(principal);
        model.addAttribute("pageTitle", "Insights");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("summaryCards", buildHomeCards(currentShop));
        return "insights/index";
    }

    @GetMapping("/sales/summary")
    public String salesSummary(
            @ModelAttribute com.expygen.insights.dto.SalesSummaryFilterRequest filter,
            @RequestParam(defaultValue = "1") int page,
            Model model,
            Principal principal
    ) {
        Shop currentShop = currentShop(principal);

        SalesSummaryPageDto summary = salesInsightsService.getSalesSummary(filter, currentShop);
        List<?> pagedInvoices = paginate(summary.getInvoices(), page, model);

        model.addAttribute("summary", summary);
        model.addAttribute("pagedInvoices", pagedInvoices);
        model.addAttribute("filter", filter);
        model.addAttribute("pageTitle", "Sales Summary");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("paginationBasePath", "/insights/sales/summary");

        return "insights/sales-summary";
    }

    @GetMapping("/business/profit-margin")
    public String profitMargin(@RequestParam(required = false) LocalDate fromDate,
                               @RequestParam(required = false) LocalDate toDate,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String manufacturer,
                               @RequestParam(defaultValue = "1") int page,
                               Model model,
                               Principal principal) {
        Shop currentShop = currentShop(principal);
        var report = profitInsightsService.buildReport(currentShop, fromDate, toDate, keyword, manufacturer);

        model.addAttribute("kpis", report.getKpis());
        model.addAttribute("manufacturerProfitSplit", report.getManufacturerProfitSplit());
        model.addAttribute("topProfitProducts", report.getTopProfitProducts());
        model.addAttribute("rows", paginate(report.getRows(), page, model));
        model.addAttribute("pageTitle", "Profit & Margin");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("keyword", keyword);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("paginationBasePath", "/insights/business/profit-margin");
        return "insights/profit-margin";
    }

    @GetMapping("/inventory/dead-stock")
    public String deadStock(@RequestParam(required = false) Integer inactivityDays,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String manufacturer,
                            @RequestParam(defaultValue = "1") int page,
                            Model model,
                            Principal principal) {
        Shop currentShop = currentShop(principal);
        var report = deadStockInsightsService.buildReport(currentShop, inactivityDays, keyword, manufacturer);

        model.addAttribute("kpis", report.getKpis());
        model.addAttribute("manufacturerSplit", report.getManufacturerSplit());
        model.addAttribute("stockAgeSplit", report.getStockAgeSplit());
        model.addAttribute("rows", paginate(report.getRows(), page, model));
        model.addAttribute("pageTitle", "Dead Stock");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("inactivityDays", inactivityDays == null || inactivityDays <= 0 ? 90 : inactivityDays);
        model.addAttribute("keyword", keyword);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("paginationBasePath", "/insights/inventory/dead-stock");
        return "insights/dead-stock";
    }

    @GetMapping(value = "/inventory/dead-stock/export", produces = "text/csv")
    public ResponseEntity<String> exportDeadStock(@RequestParam(required = false) Integer inactivityDays,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String manufacturer,
                                                  Principal principal) {
        Shop currentShop = currentShop(principal);
        List<DeadStockRowDto> rows = deadStockInsightsService.buildReport(currentShop, inactivityDays, keyword, manufacturer).getRows();
        return csvResponse("insights-dead-stock.csv",
                "Product,Manufacturer,Barcode,Current Stock,Purchase Price,Sale Price,Stock Value,Last Sold At,Days Since Last Sale,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getProductName(),
                                row.getManufacturer(),
                                row.getBarcode(),
                                row.getCurrentStock(),
                                row.getPurchasePrice(),
                                row.getSalePrice(),
                                row.getStockValue(),
                                formatDateTime(row.getLastSoldAt()),
                                row.getDaysSinceLastSale(),
                                row.getStatus())).reduce("", String::concat));
    }

    @GetMapping(value = "/business/profit-margin/export", produces = "text/csv")
    public ResponseEntity<String> exportProfitMargin(@RequestParam(required = false) LocalDate fromDate,
                                                     @RequestParam(required = false) LocalDate toDate,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String manufacturer,
                                                     Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = profitInsightsService.buildReport(currentShop, fromDate, toDate, keyword, manufacturer).getRows();
        return csvResponse("insights-profit-margin.csv",
                "Product,Manufacturer,Quantity Sold,Revenue,Estimated Cost,Gross Profit,Margin %,Last Sold At\n" +
                        rows.stream().map(row -> csvRow(
                                row.getProductName(),
                                row.getManufacturer(),
                                row.getQuantitySold(),
                                row.getRevenue(),
                                row.getEstimatedCost(),
                                row.getGrossProfit(),
                                row.getMarginPercent(),
                                formatDateTime(row.getLastSoldAt()))).reduce("", String::concat));
    }

    @GetMapping("/business/fast-slow-moving")
    public String fastSlowMoving(@RequestParam(required = false) LocalDate fromDate,
                                 @RequestParam(required = false) LocalDate toDate,
                                 @RequestParam(required = false) String movementStatus,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String manufacturer,
                                 @RequestParam(defaultValue = "1") int page,
                                 Model model,
                                 Principal principal) {
        Shop currentShop = currentShop(principal);
        var report = fastSlowMovementInsightsService.buildReport(currentShop, fromDate, toDate, movementStatus, keyword, manufacturer);

        model.addAttribute("kpis", report.getKpis());
        model.addAttribute("movementStatusSplit", report.getMovementStatusSplit());
        model.addAttribute("manufacturerUnitsSplit", report.getManufacturerUnitsSplit());
        model.addAttribute("topMovers", report.getTopMovers());
        model.addAttribute("rows", paginate(report.getRows(), page, model));
        model.addAttribute("pageTitle", "Fast / Slow Moving");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("movementStatus", movementStatus == null || movementStatus.isBlank() ? "ALL" : movementStatus);
        model.addAttribute("keyword", keyword);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("paginationBasePath", "/insights/business/fast-slow-moving");
        return "insights/fast-slow-moving";
    }

    @GetMapping(value = "/business/fast-slow-moving/export", produces = "text/csv")
    public ResponseEntity<String> exportFastSlowMoving(@RequestParam(required = false) LocalDate fromDate,
                                                       @RequestParam(required = false) LocalDate toDate,
                                                       @RequestParam(required = false) String movementStatus,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String manufacturer,
                                                       Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = fastSlowMovementInsightsService.buildReport(currentShop, fromDate, toDate, movementStatus, keyword, manufacturer).getRows();
        return csvResponse("insights-fast-slow-moving.csv",
                "Product,Manufacturer,Barcode,Current Stock,Units Sold,Invoice Count,Revenue,Avg Daily Units,Last Sold At,Stock Cover Days,Movement Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getProductName(),
                                row.getManufacturer(),
                                row.getBarcode(),
                                row.getCurrentStock(),
                                row.getUnitsSold(),
                                row.getInvoiceCount(),
                                row.getRevenue(),
                                row.getAvgDailyUnits(),
                                formatDateTime(row.getLastSoldAt()),
                                row.getStockCoverDays(),
                                row.getMovementStatus())).reduce("", String::concat));
    }

    @GetMapping("/sales/invoices")
    public String invoiceSales(@ModelAttribute com.expygen.insights.dto.SalesSummaryFilterRequest filter,
                               @RequestParam(defaultValue = "1") int page,
                               Model model,
                               Principal principal) {
        Shop currentShop = currentShop(principal);
        List<Sale> sales = salesInsightsService.findSales(filter, currentShop);
        model.addAttribute("rows", paginate(salesInsightsService.buildInvoiceRows(sales), page, model));
        model.addAttribute("filter", filter);
        model.addAttribute("pageTitle", "Invoice-wise Sales");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("paginationBasePath", "/insights/sales/invoices");
        return "insights/invoice-sales";
    }

    @GetMapping(value = "/sales/summary/export", produces = "text/csv")
    public ResponseEntity<String> exportSalesSummary(@ModelAttribute com.expygen.insights.dto.SalesSummaryFilterRequest filter,
                                                     Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = salesInsightsService.getSalesSummary(filter, currentShop).getInvoices();
        return csvResponse("insights-sales-summary.csv",
                "Invoice No,Sale Date,Customer,Payment Mode,Taxable Amount,GST Amount,Discount Amount,Total Amount,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getInvoiceNo(),
                                formatDateTime(row.getSaleDate()),
                                row.getCustomerName(),
                                row.getCustomerPhone(),
                                row.getPaymentMode(),
                                row.getTaxableAmount(),
                                row.getGstAmount(),
                                row.getDiscountAmount(),
                                row.getTotalAmount(),
                                row.getStatus())).reduce("", String::concat));
    }

    @GetMapping(value = "/sales/invoices/export", produces = "text/csv")
    public ResponseEntity<String> exportInvoiceSales(@ModelAttribute com.expygen.insights.dto.SalesSummaryFilterRequest filter,
                                                     Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = salesInsightsService.buildInvoiceRows(salesInsightsService.findSales(filter, currentShop));
        return csvResponse("insights-invoice-sales.csv",
                "Invoice No,Sale Date,Customer,Payment Mode,Taxable Amount,GST Amount,Discount Amount,Total Amount,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getInvoiceNo(),
                                formatDateTime(row.getSaleDate()),
                                row.getCustomerName(),
                                row.getPaymentMode(),
                                row.getTaxableAmount(),
                                row.getGstAmount(),
                                row.getDiscountAmount(),
                                row.getTotalAmount(),
                                row.getStatus())).reduce("", String::concat));
    }

    @GetMapping("/purchases/summary")
    public String purchaseSummary(@RequestParam(required = false) LocalDate fromDate,
                                  @RequestParam(required = false) LocalDate toDate,
                                  @RequestParam(required = false) String supplier,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") int page,
                                  Model model,
                                  Principal principal) {
        Shop currentShop = currentShop(principal);
        var purchases = purchaseInsightsService.findPurchases(currentShop, fromDate, toDate, supplier, keyword);
        var returns = purchaseInsightsService.findReturns(currentShop, fromDate, toDate);
        model.addAttribute("kpis", purchaseInsightsService.buildKpis(currentShop, purchases, returns));
        model.addAttribute("trendPoints", purchaseInsightsService.buildTrend(purchases));
        model.addAttribute("supplierContribution", purchaseInsightsService.buildSupplierContribution(purchases));
        model.addAttribute("purchaseReturnSplit", purchaseInsightsService.buildPurchaseVsReturn(purchases, returns));
        model.addAttribute("rows", paginate(purchaseInsightsService.buildRows(purchases, returns), page, model));
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("supplier", supplier);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageTitle", "Purchase Summary");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("paginationBasePath", "/insights/purchases/summary");
        return "insights/purchase-summary";
    }

    @GetMapping(value = "/purchases/summary/export", produces = "text/csv")
    public ResponseEntity<String> exportPurchaseSummary(@RequestParam(required = false) LocalDate fromDate,
                                                        @RequestParam(required = false) LocalDate toDate,
                                                        @RequestParam(required = false) String supplier,
                                                        @RequestParam(required = false) String keyword,
                                                        Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = purchaseInsightsService.buildRows(
                purchaseInsightsService.findPurchases(currentShop, fromDate, toDate, supplier, keyword),
                purchaseInsightsService.findReturns(currentShop, fromDate, toDate));
        return csvResponse("insights-purchase-summary.csv",
                "Invoice No,Purchase Date,Supplier,Items,Taxable Amount,GST Amount,Net Amount,Created By,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getInvoiceNo(),
                                row.getPurchaseDate(),
                                row.getSupplierName(),
                                row.getItemCount(),
                                row.getTaxableAmount(),
                                row.getGstAmount(),
                                row.getTotalAmount(),
                                row.getCreatedBy(),
                                row.getStatus())).reduce("", String::concat));
    }

    @GetMapping("/inventory/current-stock")
    public String currentStock(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String stockStatus,
                               @RequestParam(required = false) String expiryRange,
                               @RequestParam(required = false) String manufacturer,
                               @RequestParam(defaultValue = "1") int page,
                               Model model,
                               Principal principal) {
        Shop currentShop = currentShop(principal);
        var batches = inventoryInsightsService.findCurrentStockBatches(currentShop, keyword, stockStatus, expiryRange, manufacturer);
        model.addAttribute("kpis", inventoryInsightsService.buildCurrentStockKpis(currentShop, batches));
        model.addAttribute("stockHealth", inventoryInsightsService.buildStockHealthDistribution(batches));
        model.addAttribute("manufacturerStock", inventoryInsightsService.buildManufacturerStock(batches));
        model.addAttribute("rows", paginate(inventoryInsightsService.buildCurrentStockRows(batches), page, model));
        model.addAttribute("pageTitle", "Current Stock");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("keyword", keyword);
        model.addAttribute("stockStatus", stockStatus);
        model.addAttribute("expiryRange", expiryRange);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("paginationBasePath", "/insights/inventory/current-stock");
        return "insights/current-stock";
    }

    @GetMapping(value = "/inventory/current-stock/export", produces = "text/csv")
    public ResponseEntity<String> exportCurrentStock(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String stockStatus,
                                                     @RequestParam(required = false) String expiryRange,
                                                     @RequestParam(required = false) String manufacturer,
                                                     Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = inventoryInsightsService.buildCurrentStockRows(
                inventoryInsightsService.findCurrentStockBatches(currentShop, keyword, stockStatus, expiryRange, manufacturer));
        return csvResponse("insights-current-stock.csv",
                "Product,Batch No,Manufacturer,Barcode,Available Qty,MRP,Sale Price,Expiry Date,Stock Value,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getProductName(),
                                row.getBatchNumber(),
                                row.getManufacturer(),
                                row.getBarcode(),
                                row.getAvailableQty(),
                                row.getMrp(),
                                row.getSalePrice(),
                                row.getExpiryDate(),
                                row.getStockValue(),
                                row.getStatus())).reduce("", String::concat));
    }

    @GetMapping("/inventory/near-expiry")
    public String nearExpiry(@RequestParam(required = false) String expiryBucket,
                             @RequestParam(required = false) String manufacturer,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "1") int page,
                             Model model,
                             Principal principal) {
        Shop currentShop = currentShop(principal);
        String effectiveRange = (expiryBucket == null || expiryBucket.isBlank()) ? "90" : expiryBucket;
        var batches = inventoryInsightsService.findCurrentStockBatches(currentShop, keyword, null, effectiveRange, manufacturer);
        model.addAttribute("kpis", inventoryInsightsService.buildNearExpiryKpis(batches));
        model.addAttribute("bucketCounts", inventoryInsightsService.buildExpiryBucketCounts(batches));
        model.addAttribute("manufacturerRisk", inventoryInsightsService.buildManufacturerExpiryRisk(batches));
        model.addAttribute("valueSplit", inventoryInsightsService.buildExpiryValueSplit(batches));
        model.addAttribute("rows", paginate(inventoryInsightsService.buildNearExpiryRows(batches), page, model));
        model.addAttribute("pageTitle", "Near Expiry");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("expiryBucket", expiryBucket);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("keyword", keyword);
        model.addAttribute("paginationBasePath", "/insights/inventory/near-expiry");
        return "insights/near-expiry";
    }

    @GetMapping(value = "/inventory/near-expiry/export", produces = "text/csv")
    public ResponseEntity<String> exportNearExpiry(@RequestParam(required = false) String expiryBucket,
                                                   @RequestParam(required = false) String manufacturer,
                                                   @RequestParam(required = false) String keyword,
                                                   Principal principal) {
        Shop currentShop = currentShop(principal);
        String effectiveRange = (expiryBucket == null || expiryBucket.isBlank()) ? "90" : expiryBucket;
        var rows = inventoryInsightsService.buildNearExpiryRows(
                inventoryInsightsService.findCurrentStockBatches(currentShop, keyword, null, effectiveRange, manufacturer));
        return csvResponse("insights-near-expiry.csv",
                "Product,Batch No,Manufacturer,Available Qty,Expiry Date,Days Left,MRP,Stock Value,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getProductName(),
                                row.getBatchNumber(),
                                row.getManufacturer(),
                                row.getAvailableQty(),
                                row.getExpiryDate(),
                                row.getDaysLeft(),
                                row.getMrp(),
                                row.getStockValue(),
                        row.getStatus())).reduce("", String::concat));
    }

    @GetMapping("/inventory/expiry-loss")
    public String expiryLoss(@RequestParam(required = false) String expiryBucket,
                             @RequestParam(required = false) String manufacturer,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "1") int page,
                             Model model,
                             Principal principal) {
        Shop currentShop = currentShop(principal);
        String effectiveBucket = (expiryBucket == null || expiryBucket.isBlank()) ? "90" : expiryBucket;
        var report = expiryLossInsightsService.buildReport(currentShop, effectiveBucket, keyword, manufacturer);
        model.addAttribute("kpis", report.getKpis());
        model.addAttribute("bucketValueSplit", report.getBucketValueSplit());
        model.addAttribute("manufacturerLossSplit", report.getManufacturerLossSplit());
        model.addAttribute("rows", paginate(report.getRows(), page, model));
        model.addAttribute("pageTitle", "Expiry Loss");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("expiryBucket", effectiveBucket);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("keyword", keyword);
        model.addAttribute("paginationBasePath", "/insights/inventory/expiry-loss");
        return "insights/expiry-loss";
    }

    @GetMapping(value = "/inventory/expiry-loss/export", produces = "text/csv")
    public ResponseEntity<String> exportExpiryLoss(@RequestParam(required = false) String expiryBucket,
                                                   @RequestParam(required = false) String manufacturer,
                                                   @RequestParam(required = false) String keyword,
                                                   Principal principal) {
        Shop currentShop = currentShop(principal);
        String effectiveBucket = (expiryBucket == null || expiryBucket.isBlank()) ? "90" : expiryBucket;
        List<ExpiryLossRowDto> rows = expiryLossInsightsService.buildReport(currentShop, effectiveBucket, keyword, manufacturer).getRows();
        return csvResponse("insights-expiry-loss.csv",
                "Product,Batch No,Manufacturer,Available Qty,Expiry Date,Days From Today,Purchase Price,MRP,Sale Price,Estimated Cost Loss,Retail Value At Risk,Status\n" +
                        rows.stream().map(row -> csvRow(
                                row.getProductName(),
                                row.getBatchNumber(),
                                row.getManufacturer(),
                                row.getAvailableQty(),
                                row.getExpiryDate(),
                                row.getDaysFromToday(),
                                row.getPurchasePrice(),
                                row.getMrp(),
                                row.getSalePrice(),
                                row.getEstimatedCostLoss(),
                                row.getRetailValueAtRisk(),
                                row.getStatus())).reduce("", String::concat));
    }

    @GetMapping("/inventory/stock-adjustments")
    public String stockAdjustments(@RequestParam(required = false) LocalDate fromDate,
                                   @RequestParam(required = false) LocalDate toDate,
                                   @RequestParam(required = false) String adjustmentType,
                                   @RequestParam(required = false) String userName,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") int page,
                                   Model model,
                                   Principal principal) {
        Shop currentShop = currentShop(principal);
        List<StockAdjustment> adjustments = stockAdjustmentRepository.findByShopOrderByAdjustmentDateDescCreatedAtDesc(currentShop, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent()
                .stream()
                .filter(adj -> fromDate == null || (adj.getAdjustmentDate() != null && !adj.getAdjustmentDate().isBefore(fromDate)))
                .filter(adj -> toDate == null || (adj.getAdjustmentDate() != null && !adj.getAdjustmentDate().isAfter(toDate)))
                .filter(adj -> adjustmentType == null || adjustmentType.isBlank() || resolveAdjustmentType(adj).equalsIgnoreCase(adjustmentType))
                .filter(adj -> userName == null || userName.isBlank() || (adj.getCreatedBy() != null && contains(adj.getCreatedBy().getName(), userName)))
                .filter(adj -> keyword == null || keyword.isBlank() || contains(adj.getReason(), keyword)
                        || contains(adj.getNotes(), keyword)
                        || (adj.getProduct() != null && contains(adj.getProduct().getName(), keyword)))
                .toList();
        model.addAttribute("kpis", buildAdjustmentKpis(adjustments));
        model.addAttribute("trendPoints", buildAdjustmentTrend(adjustments));
        model.addAttribute("typeSplit", buildAdjustmentTypeSplit(adjustments));
        model.addAttribute("topProducts", buildAdjustedProducts(adjustments));
        model.addAttribute("rows", paginate(buildAdjustmentRows(adjustments), page, model));
        model.addAttribute("pageTitle", "Stock Adjustments");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("adjustmentType", adjustmentType);
        model.addAttribute("userName", userName);
        model.addAttribute("keyword", keyword);
        model.addAttribute("paginationBasePath", "/insights/inventory/stock-adjustments");
        return "insights/stock-adjustments";
    }

    @GetMapping("/inventory/barcodes")
    public String barcodeDesk(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String barcodeStatus,
                              @RequestParam(required = false) String manufacturer,
                              @RequestParam(defaultValue = "1") int page,
                              Model model,
                              Principal principal) {
        Shop currentShop = currentShop(principal);
        List<com.expygen.entity.Product> filteredProducts = productRepository.findByShopAndActiveTrue(currentShop)
                .stream()
                .filter(product -> keyword == null || keyword.isBlank()
                        || contains(product.getName(), keyword)
                        || contains(product.getGenericName(), keyword)
                        || contains(product.getBarcode(), keyword)
                        || contains(product.getManufacturer(), keyword)
                        || contains(product.getPackSize(), keyword))
                .filter(product -> manufacturer == null || manufacturer.isBlank()
                        || contains(product.getManufacturer(), manufacturer))
                .filter(product -> {
                    if (barcodeStatus == null || barcodeStatus.isBlank()) {
                        return true;
                    }
                    boolean hasBarcode = product.getBarcode() != null && !product.getBarcode().isBlank();
                    return switch (barcodeStatus) {
                        case "READY" -> hasBarcode;
                        case "MISSING" -> !hasBarcode;
                        case "RX_READY" -> hasBarcode && product.isPrescriptionRequired();
                        default -> true;
                    };
                })
                .sorted(java.util.Comparator.comparing(com.expygen.entity.Product::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        long totalProducts = filteredProducts.size();
        long barcodeReadyCount = filteredProducts.stream().filter(product -> product.getBarcode() != null && !product.getBarcode().isBlank()).count();
        long missingBarcodeCount = filteredProducts.stream().filter(product -> product.getBarcode() == null || product.getBarcode().isBlank()).count();
        long rxReadyCount = filteredProducts.stream().filter(product -> product.isPrescriptionRequired() && product.getBarcode() != null && !product.getBarcode().isBlank()).count();
        long lowStockTagged = filteredProducts.stream().filter(product -> product.getStockQuantity() != null && product.getMinStock() != null && product.getStockQuantity() <= product.getMinStock()).count();

        model.addAttribute("rows", paginate(filteredProducts, page, model));
        model.addAttribute("kpis", List.of(
                new InsightsSummaryCardDto("Visible Medicines", String.valueOf(totalProducts), "Products in current barcode desk scope"),
                new InsightsSummaryCardDto("Barcode Ready", String.valueOf(barcodeReadyCount), "Medicines ready for scanner billing and printing"),
                new InsightsSummaryCardDto("Missing Barcode", String.valueOf(missingBarcodeCount), "Products that still need internal or manufacturer barcode setup"),
                new InsightsSummaryCardDto("Rx + Barcode", String.valueOf(rxReadyCount), "Prescription medicines already ready for barcode work"),
                new InsightsSummaryCardDto("Low Stock Tagged", String.valueOf(lowStockTagged), "Low-stock medicines that already have barcode records"),
                new InsightsSummaryCardDto("Bulk Print Pages", totalProducts == 0 ? "0" : String.valueOf((int) Math.ceil(barcodeReadyCount / 40.0)), "Approx. 40-per-sheet print pages if you print all ready labels")
        ));
        model.addAttribute("pageTitle", "Barcode Desk");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("keyword", keyword);
        model.addAttribute("barcodeStatus", barcodeStatus);
        model.addAttribute("manufacturer", manufacturer);
        model.addAttribute("paginationBasePath", "/insights/inventory/barcodes");
        return "insights/barcode-desk";
    }

    @GetMapping("/setup/data-import")
    public String dataImportDesk(Model model, Principal principal) {
        Shop currentShop = currentShop(principal);
        long activeProducts = productRepository.countByShopAndActiveTrue(currentShop);

        model.addAttribute("pageTitle", "Data Import Desk");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("kpis", List.of(
                new InsightsSummaryCardDto("Products Live", String.valueOf(activeProducts), "Current active medicines ready for import comparison"),
                new InsightsSummaryCardDto("CSV Template", "1", "Starter template for product master import"),
                new InsightsSummaryCardDto("Flow Ready", "Product Import", "Opening stock and supplier lanes are staged next in this workspace"),
                new InsightsSummaryCardDto("Recommended Batch", "Up to 500", "Best import size per run for clean validation and rollback comfort")
        ));
        return "insights/data-import-desk";
    }

    @GetMapping(value = "/setup/data-import/products/template", produces = "text/csv")
    public ResponseEntity<String> downloadProductImportTemplate() {
        return csvResponse("expygen-product-import-template.csv", dataImportService.buildProductTemplateCsv());
    }

    @GetMapping(value = "/setup/data-import/opening-stock/template", produces = "text/csv")
    public ResponseEntity<String> downloadOpeningStockImportTemplate() {
        return csvResponse("expygen-opening-stock-import-template.csv", dataImportService.buildOpeningStockTemplateCsv());
    }

    @GetMapping(value = "/setup/data-import/suppliers/template", produces = "text/csv")
    public ResponseEntity<String> downloadSupplierImportTemplate() {
        return csvResponse("expygen-supplier-import-template.csv", dataImportService.buildSupplierTemplateCsv());
    }

    @PostMapping("/setup/data-import/products")
    public String importProductsFromCsv(@RequestParam("file") MultipartFile file,
                                        RedirectAttributes redirectAttributes,
                                        Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop currentShop = currentUser.getShop();

        try {
            var result = dataImportService.importProducts(currentShop, file);
            redirectAttributes.addFlashAttribute("productImportResult", result);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Product import completed: " + result.getImportedCount() + " imported, "
                            + result.getSkippedCount() + " skipped, "
                            + result.getFailedCount() + " failed.");
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentShop,
                    "PRODUCT_IMPORT_RUN",
                    "ProductImport",
                    null,
                    "SUCCESS",
                    null,
                    result,
                    "CSV product import completed from Insights Data Import Desk");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("importError", ex.getMessage());
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentShop,
                    "PRODUCT_IMPORT_RUN",
                    "ProductImport",
                    null,
                    "FAILED",
                    null,
                    null,
                    ex.getMessage());
        }

        return "redirect:/insights/setup/data-import";
    }

    @PostMapping("/setup/data-import/opening-stock")
    public String importOpeningStockFromCsv(@RequestParam("file") MultipartFile file,
                                            RedirectAttributes redirectAttributes,
                                            Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop currentShop = currentUser.getShop();

        try {
            var result = dataImportService.importOpeningStock(currentShop, currentUser, file);
            redirectAttributes.addFlashAttribute("openingStockImportResult", result);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Opening stock import completed: " + result.getImportedCount() + " imported, "
                            + result.getSkippedCount() + " skipped, "
                            + result.getFailedCount() + " failed.");
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentShop,
                    "OPENING_STOCK_IMPORT_RUN",
                    "OpeningStockImport",
                    null,
                    "SUCCESS",
                    null,
                    result,
                    "CSV opening stock import completed from Insights Data Import Desk");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("importError", ex.getMessage());
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentShop,
                    "OPENING_STOCK_IMPORT_RUN",
                    "OpeningStockImport",
                    null,
                    "FAILED",
                    null,
                    null,
                    ex.getMessage());
        }

        return "redirect:/insights/setup/data-import";
    }

    @PostMapping("/setup/data-import/suppliers")
    public String importSuppliersFromCsv(@RequestParam("file") MultipartFile file,
                                         RedirectAttributes redirectAttributes,
                                         Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop currentShop = currentUser.getShop();

        try {
            var result = dataImportService.importSuppliers(currentShop, file);
            redirectAttributes.addFlashAttribute("supplierImportResult", result);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Supplier import completed: " + result.getImportedCount() + " imported, "
                            + result.getSkippedCount() + " skipped, "
                            + result.getFailedCount() + " failed.");
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentShop,
                    "SUPPLIER_IMPORT_RUN",
                    "SupplierImport",
                    null,
                    "SUCCESS",
                    null,
                    result,
                    "CSV supplier import completed from Insights Data Import Desk");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("importError", ex.getMessage());
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentShop,
                    "SUPPLIER_IMPORT_RUN",
                    "SupplierImport",
                    null,
                    "FAILED",
                    null,
                    null,
                    ex.getMessage());
        }

        return "redirect:/insights/setup/data-import";
    }

    @GetMapping(value = "/inventory/stock-adjustments/export", produces = "text/csv")
    public ResponseEntity<String> exportStockAdjustments(@RequestParam(required = false) LocalDate fromDate,
                                                         @RequestParam(required = false) LocalDate toDate,
                                                         @RequestParam(required = false) String adjustmentType,
                                                         @RequestParam(required = false) String userName,
                                                         @RequestParam(required = false) String keyword,
                                                         Principal principal) {
        Shop currentShop = currentShop(principal);
        List<StockAdjustment> adjustments = stockAdjustmentRepository.findByShopOrderByAdjustmentDateDescCreatedAtDesc(currentShop, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent()
                .stream()
                .filter(adj -> fromDate == null || (adj.getAdjustmentDate() != null && !adj.getAdjustmentDate().isBefore(fromDate)))
                .filter(adj -> toDate == null || (adj.getAdjustmentDate() != null && !adj.getAdjustmentDate().isAfter(toDate)))
                .filter(adj -> adjustmentType == null || adjustmentType.isBlank() || resolveAdjustmentType(adj).equalsIgnoreCase(adjustmentType))
                .filter(adj -> userName == null || userName.isBlank() || (adj.getCreatedBy() != null && contains(adj.getCreatedBy().getName(), userName)))
                .filter(adj -> keyword == null || keyword.isBlank() || contains(adj.getReason(), keyword)
                        || contains(adj.getNotes(), keyword)
                        || (adj.getProduct() != null && contains(adj.getProduct().getName(), keyword)))
                .toList();
        var rows = buildAdjustmentRows(adjustments);
        return csvResponse("insights-stock-adjustments.csv",
                "Timestamp,Product,Batch No,Type,Previous Qty,Changed Qty,New Qty,Reason,User\n" +
                        rows.stream().map(row -> csvRow(
                                formatDateTime(row.getTimestamp()),
                                row.getProductName(),
                                row.getBatchNumber(),
                                row.getAdjustmentType(),
                                row.getPreviousQty(),
                                row.getChangedQty(),
                                row.getNewQty(),
                                row.getReason(),
                                row.getUserName())).reduce("", String::concat));
    }

    @GetMapping("/audit/activity")
    public String activityAudit(@RequestParam(required = false) LocalDate fromDate,
                                @RequestParam(required = false) LocalDate toDate,
                                @RequestParam(required = false) String module,
                                @RequestParam(required = false) String userName,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(defaultValue = "1") int page,
                                Model model,
                                Principal principal) {
        Shop currentShop = currentShop(principal);
        var logs = auditInsightsService.findAuditLogs(currentShop, fromDate, toDate, module, userName, keyword);
        model.addAttribute("kpis", auditInsightsService.buildKpis(logs));
        model.addAttribute("trendPoints", auditInsightsService.buildTrend(logs));
        model.addAttribute("moduleSplit", auditInsightsService.buildModuleDistribution(logs));
        model.addAttribute("topUsers", auditInsightsService.buildTopUsers(logs));
        model.addAttribute("rows", paginate(auditInsightsService.buildRows(logs), page, model));
        model.addAttribute("pageTitle", "Activity Audit");
        model.addAttribute("activeMenu", "insights");
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("module", module);
        model.addAttribute("userName", userName);
        model.addAttribute("keyword", keyword);
        model.addAttribute("paginationBasePath", "/insights/audit/activity");
        return "insights/activity-audit";
    }

    @GetMapping(value = "/audit/activity/export", produces = "text/csv")
    public ResponseEntity<String> exportActivityAudit(@RequestParam(required = false) LocalDate fromDate,
                                                      @RequestParam(required = false) LocalDate toDate,
                                                      @RequestParam(required = false) String module,
                                                      @RequestParam(required = false) String userName,
                                                      @RequestParam(required = false) String keyword,
                                                      Principal principal) {
        Shop currentShop = currentShop(principal);
        var rows = auditInsightsService.buildRows(auditInsightsService.findAuditLogs(currentShop, fromDate, toDate, module, userName, keyword));
        return csvResponse("insights-activity-audit.csv",
                "Timestamp,User,Module,Action,Reference,Details,Priority\n" +
                        rows.stream().map(row -> csvRow(
                                formatDateTime(row.getTimestamp()),
                                row.getUserName(),
                                row.getModule(),
                                row.getAction(),
                                row.getReference(),
                                row.getDetails(),
                                row.getPriority())).reduce("", String::concat));
    }

    private <T> List<T> paginate(List<T> source, int page, Model model) {
        List<T> safeSource = source == null ? Collections.emptyList() : source;
        int totalPages = Math.max(1, (int) Math.ceil((double) safeSource.size() / INSIGHTS_PAGE_SIZE));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * INSIGHTS_PAGE_SIZE, safeSource.size());
        int toIndex = Math.min(fromIndex + INSIGHTS_PAGE_SIZE, safeSource.size());

        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPreviousPage", currentPage > 1);
        model.addAttribute("hasNextPage", currentPage < totalPages);
        model.addAttribute("previousPage", Math.max(1, currentPage - 1));
        model.addAttribute("nextPage", Math.min(totalPages, currentPage + 1));
        model.addAttribute("pageSize", INSIGHTS_PAGE_SIZE);
        model.addAttribute("totalRows", safeSource.size());

        return safeSource.subList(fromIndex, toIndex);
    }

    private Shop currentShop(Principal principal) {
        return shopService.getShopByUsername(principal.getName());
    }

    private List<InsightsSummaryCardDto> buildHomeCards(Shop shop) {
        var salesSummary = salesInsightsService.getSalesSummary(new com.expygen.insights.dto.SalesSummaryFilterRequest(), shop);
        var stockBatches = inventoryInsightsService.findCurrentStockBatches(shop, null, null, null, null);
        var purchaseRows = purchaseInsightsService.findPurchases(shop, null, null, null, null);
        var returns = purchaseInsightsService.findReturns(shop, null, null);
        var adjustments = stockAdjustmentRepository.findByShopOrderByAdjustmentDateDescCreatedAtDesc(shop, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();

        return List.of(
                new InsightsSummaryCardDto("Today Sales", "₹" + salesSummary.getKpis().getTotalSales(), "Live sales snapshot for current filter window"),
                new InsightsSummaryCardDto("Invoices", String.valueOf(salesSummary.getKpis().getInvoiceCount()), "Completed invoice count"),
                new InsightsSummaryCardDto("Current Stock Value", formatMoney(stockBatches.stream().mapToDouble(batch -> (batch.getSalePrice() != null ? batch.getSalePrice().doubleValue() : 0.0) * (batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : 0)).sum()), "Total visible inventory value"),
                new InsightsSummaryCardDto("Near Expiry Batches", String.valueOf(stockBatches.stream().filter(batch -> batch.getExpiryDate() != null && !batch.getExpiryDate().isAfter(LocalDate.now().plusDays(90))).count()), "Batches approaching expiry"),
                new InsightsSummaryCardDto("Purchase Value", formatMoney(purchaseRows.stream().mapToDouble(entry -> entry.getTotalAmount() != null ? entry.getTotalAmount().doubleValue() : 0.0).sum()), "Procurement recorded in current insight scope"),
                new InsightsSummaryCardDto("Stock Adjustments", String.valueOf(adjustments.size()), "Manual inventory corrections tracked")
        );
    }

    private String resolveAdjustmentType(StockAdjustment adjustment) {
        String reason = adjustment.getReason() != null ? adjustment.getReason().toUpperCase() : "";
        if (reason.contains("DAMAGE")) return "DAMAGED";
        if (reason.contains("EXPIRE")) return "EXPIRED";
        if (adjustment.getQuantityDelta() != null && adjustment.getQuantityDelta() > 0) return "INCREASE";
        if (adjustment.getQuantityDelta() != null && adjustment.getQuantityDelta() < 0) return "DECREASE";
        return "CORRECTION";
    }

    private List<InsightsSummaryCardDto> buildAdjustmentKpis(List<StockAdjustment> adjustments) {
        long total = adjustments.size();
        long increased = adjustments.stream().filter(adj -> adj.getQuantityDelta() != null && adj.getQuantityDelta() > 0).mapToLong(adj -> adj.getQuantityDelta()).sum();
        long reduced = adjustments.stream().filter(adj -> adj.getQuantityDelta() != null && adj.getQuantityDelta() < 0).mapToLong(adj -> Math.abs(adj.getQuantityDelta())).sum();
        long damaged = adjustments.stream().filter(adj -> "DAMAGED".equals(resolveAdjustmentType(adj))).count();
        long expired = adjustments.stream().filter(adj -> "EXPIRED".equals(resolveAdjustmentType(adj))).count();
        long users = adjustments.stream().map(adj -> adj.getCreatedBy() != null ? adj.getCreatedBy().getId() : null).filter(id -> id != null).distinct().count();
        return List.of(
                new InsightsSummaryCardDto("Total Adjustments", String.valueOf(total), "Recorded stock correction events"),
                new InsightsSummaryCardDto("Qty Increased", String.valueOf(increased), "Stock added through reconciliation or correction"),
                new InsightsSummaryCardDto("Qty Reduced", String.valueOf(reduced), "Stock reduced due to correction or loss"),
                new InsightsSummaryCardDto("Damaged Cases", String.valueOf(damaged), "Adjustments due to damaged stock"),
                new InsightsSummaryCardDto("Expired Cases", String.valueOf(expired), "Stock removed due to expiry"),
                new InsightsSummaryCardDto("Users Involved", String.valueOf(users), "Users performing stock adjustments")
        );
    }

    private List<com.expygen.insights.dto.SalesTrendPointDto> buildAdjustmentTrend(List<StockAdjustment> adjustments) {
        return adjustments.stream()
                .filter(adj -> adj.getAdjustmentDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        StockAdjustment::getAdjustmentDate,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> new com.expygen.insights.dto.SalesTrendPointDto(e.getKey().toString(), e.getValue().doubleValue()))
                .toList();
    }

    private List<PaymentModeSummaryDto> buildAdjustmentTypeSplit(List<StockAdjustment> adjustments) {
        return adjustments.stream()
                .collect(java.util.stream.Collectors.groupingBy(this::resolveAdjustmentType, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> new PaymentModeSummaryDto(e.getKey(), e.getValue().doubleValue()))
                .toList();
    }

    private List<PaymentModeSummaryDto> buildAdjustedProducts(List<StockAdjustment> adjustments) {
        return adjustments.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        adj -> adj.getProduct() != null ? adj.getProduct().getName() : "Unknown",
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new PaymentModeSummaryDto(e.getKey(), e.getValue().doubleValue()))
                .toList();
    }

    private List<StockAdjustmentRowDto> buildAdjustmentRows(List<StockAdjustment> adjustments) {
        return adjustments.stream()
                .map(adj -> new StockAdjustmentRowDto(
                        adj.getId(),
                        adj.getCreatedAt(),
                        adj.getProduct() != null ? adj.getProduct().getName() : "Unknown Product",
                        adj.getPurchaseBatch() != null ? adj.getPurchaseBatch().getBatchNumber() : "-",
                        resolveAdjustmentType(adj),
                        adj.getPurchaseBatch() != null ? adj.getPreviousBatchQuantity() : adj.getPreviousProductStock(),
                        adj.getQuantityDelta(),
                        adj.getPurchaseBatch() != null ? adj.getNewBatchQuantity() : adj.getNewProductStock(),
                        adj.getReason() != null && !adj.getReason().isBlank() ? adj.getReason() : adj.getNotes(),
                        adj.getCreatedBy() != null ? adj.getCreatedBy().getName() : "System"
                ))
                .toList();
    }

    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && !keyword.isBlank() && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String formatMoney(double value) {
        return "₹" + String.format(java.util.Locale.ENGLISH, "%,.2f", value);
    }

    private ResponseEntity<String> csvResponse(String filename, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(content);
    }

    private String csvRow(Object... columns) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvEscape(columns[i]));
        }
        builder.append('\n');
        return builder.toString();
    }

    private String csvEscape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a", Locale.ENGLISH));
    }
}
