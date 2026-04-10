package com.expygen.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expygen.dto.PurchaseEntryForm;
import com.expygen.dto.ExpiryAlertSummary;
import com.expygen.dto.ExpiryReportBucket;
import com.expygen.dto.ExpiryReportItem;
import com.expygen.dto.PurchaseReturnForm;
import com.expygen.dto.StockAdjustmentForm;
import com.expygen.entity.PlanType;
import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.PurchaseReturn;
import com.expygen.entity.Shop;
import com.expygen.entity.StockAdjustment;
import com.expygen.entity.Supplier;
import com.expygen.entity.User;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.PurchaseBatchRepository;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.PurchaseReturnRepository;
import com.expygen.repository.StockAdjustmentRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.InventoryControlService;
import com.expygen.service.PurchaseService;
import com.expygen.service.ExpiryAlertService;
import com.expygen.service.SupplierService;
import com.expygen.repository.SupplierRepository;
import com.expygen.dto.SupplierForm;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/purchases")
public class PurchaseController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PurchaseEntryRepository purchaseEntryRepository;
    private final PurchaseBatchRepository purchaseBatchRepository;
    private final PurchaseService purchaseService;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final InventoryControlService inventoryControlService;
    private final ExpiryAlertService expiryAlertService;
    private final SupplierRepository supplierRepository;
    private final SupplierService supplierService;

    @GetMapping
    public String listPurchases(@RequestParam(defaultValue = "0") int purchasePage,
                                @RequestParam(defaultValue = "0") int batchPage,
                                Model model,
                                Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();
        LocalDate today = LocalDate.now();
        ExpiryAlertSummary expirySummary = expiryAlertService.buildAlertSummary(shop);

        int safePurchasePage = Math.max(purchasePage, 0);
        int safeBatchPage = Math.max(batchPage, 0);

        Page<PurchaseEntry> purchaseEntryPage = purchaseEntryRepository
                .findByShopOrderByPurchaseDateDescIdDesc(shop, PageRequest.of(safePurchasePage, 10));
        Page<PurchaseBatch> batchLedgerPage = purchaseBatchRepository
                .findByShopAndActiveTrueOrderByCreatedAtDesc(shop, PageRequest.of(safeBatchPage, 10));

        List<PurchaseEntry> purchases = purchaseEntryPage.getContent();
        List<PurchaseBatch> batches = batchLedgerPage.getContent();
        List<ExpiryReportItem> nearExpiryBatches = expiryAlertService.buildReportItems(shop, ExpiryReportBucket.DAYS_60, 6);

        long purchaseCount = purchaseEntryRepository.countByShop(shop);
        long nearExpiryCount = expirySummary.getExpiringIn60DaysCount();
        long expiredCount = expirySummary.getExpiredBatchCount();
        long liveBatchCount = batches.stream().filter(batch -> batch.getAvailableQuantity() != null && batch.getAvailableQuantity() > 0).count();
        Long availableUnits = purchaseBatchRepository.sumAvailableQuantityByShop(shop);
        BigDecimal totalPurchasedValue = purchaseEntryRepository.sumTotalAmountByShop(shop);

        model.addAttribute("purchases", purchases);
        model.addAttribute("batches", batches);
        model.addAttribute("purchasePageNumber", purchaseEntryPage.getNumber());
        model.addAttribute("purchaseTotalPages", purchaseEntryPage.getTotalPages());
        model.addAttribute("batchPageNumber", batchLedgerPage.getNumber());
        model.addAttribute("batchTotalPages", batchLedgerPage.getTotalPages());
        model.addAttribute("nearExpiryBatches", nearExpiryBatches);
        model.addAttribute("purchaseCount", purchaseCount);
        model.addAttribute("nearExpiryCount", nearExpiryCount);
        model.addAttribute("expiredBatchCount", expiredCount);
        model.addAttribute("criticalAlertCount", expirySummary.getCriticalAlertCount());
        model.addAttribute("supplierCount", supplierRepository.countByShopAndActiveTrue(shop));
        model.addAttribute("liveBatchCount", liveBatchCount);
        model.addAttribute("availableBatchUnits", availableUnits != null ? availableUnits : 0L);
        model.addAttribute("totalPurchasedValue", totalPurchasedValue != null ? totalPurchasedValue : BigDecimal.ZERO);

        populateShellModel(model, user, shop);
        return "purchases";
    }

    @GetMapping("/view/{id}")
    public String viewPurchase(@PathVariable Long id,
                               Model model,
                               Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        PurchaseEntry purchase = purchaseEntryRepository.findByIdAndShop(id, shop)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase entry not found"));
        List<PurchaseBatch> batches = purchaseBatchRepository.findByPurchaseEntryOrderByExpiryDateAscIdAsc(purchase);

        model.addAttribute("purchase", purchase);
        model.addAttribute("batches", batches);
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
        return "purchase-detail";
    }

    @GetMapping("/expiry-report")
    public String expiryReport(@RequestParam(defaultValue = "critical") String bucket,
                               Model model,
                               Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();
        ExpiryReportBucket selectedBucket = ExpiryReportBucket.fromCode(bucket);

        model.addAttribute("expirySummary", expiryAlertService.buildAlertSummary(shop));
        model.addAttribute("expiryBuckets", ExpiryReportBucket.values());
        model.addAttribute("selectedExpiryBucket", selectedBucket);
        model.addAttribute("expiryBatches", expiryAlertService.buildReportItems(shop, selectedBucket, 250));
        model.addAttribute("criticalExpiryBatches", expiryAlertService.buildReportItems(shop, ExpiryReportBucket.CRITICAL, 8));
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
        return "expiry-report";
    }

    @GetMapping("/suppliers")
    public String listSuppliers(Model model, Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();
        LocalDate today = LocalDate.now();

        List<Supplier> suppliers = supplierService.listActiveSuppliers(shop);
        Map<Long, Long> purchaseCounts = new LinkedHashMap<>();
        Map<Long, BigDecimal> purchaseValues = new LinkedHashMap<>();
        Map<Long, Long> returnCounts = new LinkedHashMap<>();
        Map<Long, BigDecimal> returnValues = new LinkedHashMap<>();
        Map<Long, Long> liveUnits = new LinkedHashMap<>();
        Map<Long, Long> nearExpiryCounts = new LinkedHashMap<>();

        for (Supplier supplier : suppliers) {
            purchaseCounts.put(supplier.getId(), purchaseEntryRepository.countByShopAndSupplier(shop, supplier));
            purchaseValues.put(supplier.getId(), nonNullMoney(purchaseEntryRepository.sumTotalAmountByShopAndSupplier(shop, supplier)));
            returnCounts.put(supplier.getId(), purchaseReturnRepository.countByShopAndSupplier(shop, supplier));
            returnValues.put(supplier.getId(), nonNullMoney(purchaseReturnRepository.sumTotalAmountByShopAndSupplier(shop, supplier)));
            liveUnits.put(supplier.getId(), nonNullLong(purchaseBatchRepository.sumAvailableQuantityBySupplier(shop, supplier)));
            nearExpiryCounts.put(supplier.getId(), purchaseBatchRepository.countNearExpiryBatchesBySupplier(shop, supplier, today, today.plusDays(60)));
        }

        model.addAttribute("suppliers", suppliers);
        model.addAttribute("purchaseCounts", purchaseCounts);
        model.addAttribute("purchaseValues", purchaseValues);
        model.addAttribute("returnCounts", returnCounts);
        model.addAttribute("returnValues", returnValues);
        model.addAttribute("liveUnits", liveUnits);
        model.addAttribute("nearExpiryCounts", nearExpiryCounts);
        model.addAttribute("supplierCount", suppliers.size());
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
        return "suppliers";
    }

    @GetMapping("/suppliers/new")
    public String newSupplierForm(Model model, Authentication authentication) {
        User user = getUser(authentication);
        populateSupplierFormModel(model, user, user.getShop(), supplierService.newSupplierForm());
        return "supplier-form";
    }

    @GetMapping("/suppliers/{id}/edit")
    public String editSupplierForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();
        Supplier supplier = supplierRepository.findByIdAndShop(id, shop)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        populateSupplierFormModel(model, user, shop, supplierService.toForm(supplier));
        return "supplier-form";
    }

    @PostMapping("/suppliers/save")
    public String saveSupplier(@ModelAttribute("supplierForm") SupplierForm supplierForm,
                               Authentication authentication,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        try {
            Supplier supplier = supplierService.saveSupplier(shop, supplierForm);
            redirectAttributes.addFlashAttribute("success", "Supplier saved for " + supplier.getName() + ".");
            return "redirect:/purchases/suppliers/" + supplier.getId();
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            populateSupplierFormModel(model, user, shop, supplierForm);
            return "supplier-form";
        }
    }

    @GetMapping("/suppliers/{id}")
    public String viewSupplier(@PathVariable Long id,
                               Model model,
                               Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();
        LocalDate today = LocalDate.now();

        Supplier supplier = supplierRepository.findByIdAndShop(id, shop)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

        List<PurchaseEntry> purchases = purchaseEntryRepository
                .findByShopAndSupplierOrderByPurchaseDateDescIdDesc(shop, supplier, PageRequest.of(0, 10))
                .getContent();
        List<PurchaseReturn> returns = purchaseReturnRepository
                .findByShopAndSupplierOrderByReturnDateDescIdDesc(shop, supplier, PageRequest.of(0, 10))
                .getContent();
        List<PurchaseBatch> nearExpiryBatches = purchaseBatchRepository.findTopNearExpiryBatchesBySupplier(
                shop,
                supplier,
                today,
                today.plusDays(60),
                PageRequest.of(0, 8));

        model.addAttribute("supplier", supplier);
        model.addAttribute("purchases", purchases);
        model.addAttribute("purchaseReturns", returns);
        model.addAttribute("nearExpiryBatches", nearExpiryBatches);
        model.addAttribute("purchaseCount", purchaseEntryRepository.countByShopAndSupplier(shop, supplier));
        model.addAttribute("purchaseValue", nonNullMoney(purchaseEntryRepository.sumTotalAmountByShopAndSupplier(shop, supplier)));
        model.addAttribute("returnCount", purchaseReturnRepository.countByShopAndSupplier(shop, supplier));
        model.addAttribute("returnValue", nonNullMoney(purchaseReturnRepository.sumTotalAmountByShopAndSupplier(shop, supplier)));
        model.addAttribute("liveBatchUnits", nonNullLong(purchaseBatchRepository.sumAvailableQuantityBySupplier(shop, supplier)));
        model.addAttribute("nearExpiryCount", purchaseBatchRepository.countNearExpiryBatchesBySupplier(shop, supplier, today, today.plusDays(60)));
        model.addAttribute("expiredBatchCount", purchaseBatchRepository.countExpiredBatchesBySupplier(shop, supplier, today));
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
        return "supplier-detail";
    }

    @GetMapping("/new")
    public String newPurchaseForm(Model model, Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        populatePurchaseFormModel(model, user, shop, purchaseService.newEntryForm());
        return "purchase-form";
    }

    @PostMapping("/save")
    public String savePurchase(@ModelAttribute("purchaseForm") PurchaseEntryForm purchaseForm,
                               Authentication authentication,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        try {
            PurchaseEntry savedEntry = purchaseService.recordPurchase(purchaseForm, shop, user);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Purchase saved for " + savedEntry.getSupplierName() + " with " + savedEntry.getBatches().size() + " batch line(s).");
            return "redirect:/purchases";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            populatePurchaseFormModel(model, user, shop, ensureAtLeastOneLine(purchaseForm));
            return "purchase-form";
        }
    }

    @GetMapping("/returns")
    public String listPurchaseReturns(Model model, Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();
        LocalDate today = LocalDate.now();

        List<PurchaseReturn> returns = purchaseReturnRepository
                .findByShopOrderByReturnDateDescIdDesc(shop, PageRequest.of(0, 12))
                .getContent();

        long returnCount = purchaseReturnRepository.countByShop(shop);
        BigDecimal totalReturnValue = purchaseReturnRepository.sumTotalAmountByShop(shop);
        Long returnedUnits = purchaseReturnRepository.sumReturnedUnitsByShopAndDateBetween(
                shop,
                today.minusDays(30),
                today);

        model.addAttribute("purchaseReturns", returns);
        model.addAttribute("returnCount", returnCount);
        model.addAttribute("returnedUnits", returnedUnits != null ? returnedUnits : 0L);
        model.addAttribute("totalReturnValue", totalReturnValue != null ? totalReturnValue : BigDecimal.ZERO);
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
        return "purchase-returns";
    }

    @GetMapping("/returns/new")
    public String newPurchaseReturnForm(@RequestParam(required = false) Long batchId,
                                        Model model,
                                        Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        PurchaseReturnForm form = inventoryControlService.newReturnForm();
        if (batchId != null && !form.getItems().isEmpty()) {
            form.getItems().get(0).setPurchaseBatchId(batchId);
        }
        populatePurchaseReturnFormModel(model, user, shop, form);
        return "purchase-return-form";
    }

    @PostMapping("/returns/save")
    public String savePurchaseReturn(@ModelAttribute("purchaseReturnForm") PurchaseReturnForm purchaseReturnForm,
                                     Authentication authentication,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        try {
            PurchaseReturn savedReturn = inventoryControlService.recordPurchaseReturn(purchaseReturnForm, shop, user);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Purchase return saved for " + savedReturn.getSupplierName() + " with " + savedReturn.getLines().size() + " line(s).");
            return "redirect:/purchases/returns";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            populatePurchaseReturnFormModel(model, user, shop, ensureAtLeastOneReturnLine(purchaseReturnForm));
            return "purchase-return-form";
        }
    }

    @GetMapping("/adjustments")
    public String listStockAdjustments(Model model, Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        List<StockAdjustment> adjustments = stockAdjustmentRepository
                .findByShopOrderByAdjustmentDateDescCreatedAtDesc(shop, PageRequest.of(0, 16))
                .getContent();

        long adjustmentCount = stockAdjustmentRepository.countByShop(shop);
        Long netAdjustedUnits = stockAdjustmentRepository.sumQuantityDeltaByShop(shop);
        long negativeAdjustments = adjustments.stream()
                .filter(adjustment -> adjustment.getQuantityDelta() != null && adjustment.getQuantityDelta() < 0)
                .count();

        model.addAttribute("stockAdjustments", adjustments);
        model.addAttribute("adjustmentCount", adjustmentCount);
        model.addAttribute("netAdjustedUnits", netAdjustedUnits != null ? netAdjustedUnits : 0L);
        model.addAttribute("negativeAdjustments", negativeAdjustments);
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
        return "stock-adjustments";
    }

    @GetMapping("/adjustments/new")
    public String newStockAdjustmentForm(@RequestParam(required = false) Long batchId,
                                         Model model,
                                         Authentication authentication) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        StockAdjustmentForm form = inventoryControlService.newStockAdjustmentForm();
        if (batchId != null) {
            form.setPurchaseBatchId(batchId);
        }
        populateStockAdjustmentFormModel(model, user, shop, form);
        return "stock-adjustment-form";
    }

    @PostMapping("/adjustments/save")
    public String saveStockAdjustment(@ModelAttribute("stockAdjustmentForm") StockAdjustmentForm stockAdjustmentForm,
                                      Authentication authentication,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Shop shop = user.getShop();

        try {
            StockAdjustment savedAdjustment = inventoryControlService.recordStockAdjustment(stockAdjustmentForm, shop, user);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Stock adjusted for " + savedAdjustment.getProduct().getName() + " (" + savedAdjustment.getQuantityDelta() + " units).");
            return "redirect:/purchases/adjustments";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            populateStockAdjustmentFormModel(model, user, shop, stockAdjustmentForm);
            return "stock-adjustment-form";
        }
    }

    private PurchaseEntryForm ensureAtLeastOneLine(PurchaseEntryForm form) {
        if (form.getItems() == null || form.getItems().isEmpty()) {
            return purchaseService.newEntryForm();
        }
        return form;
    }

    private PurchaseReturnForm ensureAtLeastOneReturnLine(PurchaseReturnForm form) {
        if (form.getItems() == null || form.getItems().isEmpty()) {
            return inventoryControlService.newReturnForm();
        }
        return form;
    }

    private void populatePurchaseFormModel(Model model, User user, Shop shop, PurchaseEntryForm form) {
        List<Product> products = productRepository.findByShopAndActiveTrue(shop).stream()
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        model.addAttribute("purchaseForm", form);
        model.addAttribute("products", products);
        model.addAttribute("suppliers", supplierService.listTopSuppliers(shop, 40));
        populateShellModel(model, user, shop);
    }

    private void populatePurchaseReturnFormModel(Model model, User user, Shop shop, PurchaseReturnForm form) {
        List<PurchaseBatch> batches = purchaseBatchRepository.findByShopAndActiveTrueOrderByCreatedAtDesc(shop, PageRequest.of(0, 250))
                .getContent();

        model.addAttribute("purchaseReturnForm", form);
        model.addAttribute("batches", batches);
        populateShellModel(model, user, shop);
    }

    private void populateStockAdjustmentFormModel(Model model, User user, Shop shop, StockAdjustmentForm form) {
        List<Product> products = productRepository.findByShopAndActiveTrue(shop).stream()
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<PurchaseBatch> batches = purchaseBatchRepository.findByShopAndActiveTrueOrderByCreatedAtDesc(shop, PageRequest.of(0, 250))
                .getContent();

        model.addAttribute("stockAdjustmentForm", form);
        model.addAttribute("products", products);
        model.addAttribute("batches", batches);
        populateShellModel(model, user, shop);
    }

    private void populateSupplierFormModel(Model model, User user, Shop shop, SupplierForm form) {
        model.addAttribute("supplierForm", form);
        populateShellModel(model, user, shop);
        model.addAttribute("currentPageName", "purchases");
    }

    private void populateShellModel(Model model, User user, Shop shop) {
        model.addAttribute("user", user);
        model.addAttribute("shop", shop);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("currentPage", "purchases");
        PlanType planType = shop.getPlanType();
        model.addAttribute("planType", planType != null ? planType.name() : "FREE");
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private BigDecimal nonNullMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private long nonNullLong(Long value) {
        return value != null ? value : 0L;
    }
}
