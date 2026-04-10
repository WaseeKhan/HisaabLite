package com.expygen.insights.service;

import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.PurchaseReturn;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.insights.dto.PurchaseSummaryRowDto;
import com.expygen.insights.dto.SalesTrendPointDto;
import com.expygen.repository.PurchaseEntryRepository;
import com.expygen.repository.PurchaseReturnRepository;
import com.expygen.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseInsightsService {

    private final PurchaseEntryRepository purchaseEntryRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SupplierRepository supplierRepository;

    public List<PurchaseEntry> findPurchases(Shop shop, LocalDate fromDate, LocalDate toDate, String supplierKeyword, String keyword) {
        return purchaseEntryRepository.findAll().stream()
                .filter(entry -> entry.getShop() != null && entry.getShop().getId().equals(shop.getId()))
                .filter(entry -> matchesDate(entry.getPurchaseDate(), fromDate, toDate))
                .filter(entry -> contains(entry.getSupplierName(), supplierKeyword)
                        || contains(entry.getSupplier() != null ? entry.getSupplier().getName() : null, supplierKeyword)
                        || isBlank(supplierKeyword))
                .filter(entry -> isBlank(keyword)
                        || contains(entry.getSupplierInvoiceNumber(), keyword)
                        || contains(entry.getSupplierName(), keyword)
                        || contains(entry.getNotes(), keyword)
                        || entry.getBatches().stream().anyMatch(batch ->
                                contains(batch.getBatchNumber(), keyword)
                                        || contains(batch.getProduct() != null ? batch.getProduct().getName() : null, keyword)))
                .sorted(Comparator.comparing(PurchaseEntry::getPurchaseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PurchaseEntry::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<PurchaseReturn> findReturns(Shop shop, LocalDate fromDate, LocalDate toDate) {
        return purchaseReturnRepository.findAll().stream()
                .filter(entry -> entry.getShop() != null && entry.getShop().getId().equals(shop.getId()))
                .filter(entry -> matchesDate(entry.getReturnDate(), fromDate, toDate))
                .sorted(Comparator.comparing(PurchaseReturn::getReturnDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PurchaseReturn::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<PurchaseSummaryRowDto> buildRows(List<PurchaseEntry> purchases, List<PurchaseReturn> returns) {
        Map<String, Double> returnedByInvoice = returns.stream()
                .collect(Collectors.groupingBy(
                        ret -> safeString(ret.getReferenceInvoiceNumber()),
                        Collectors.summingDouble(ret -> safe(ret.getTotalAmount()))));

        return purchases.stream()
                .map(entry -> new PurchaseSummaryRowDto(
                        entry.getId(),
                        safeString(entry.getSupplierInvoiceNumber(), "PUR-" + entry.getId()),
                        entry.getPurchaseDate(),
                        safeString(entry.getSupplierName(), entry.getSupplier() != null ? entry.getSupplier().getName() : "Unknown Supplier"),
                        entry.getBatches() != null ? entry.getBatches().size() : 0,
                        round(safe(entry.getTotalAmount()) - returnedByInvoice.getOrDefault(safeString(entry.getSupplierInvoiceNumber()), 0.0)),
                        0.0,
                        round(safe(entry.getTotalAmount())),
                        entry.getCreatedBy() != null ? safeString(entry.getCreatedBy().getName(), entry.getCreatedBy().getUsername()) : "System",
                        returnedByInvoice.getOrDefault(safeString(entry.getSupplierInvoiceNumber()), 0.0) > 0 ? "Returned Partial" : "Posted"
                ))
                .toList();
    }

    public List<SalesTrendPointDto> buildTrend(List<PurchaseEntry> purchases) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        return purchases.stream()
                .filter(entry -> entry.getPurchaseDate() != null)
                .collect(Collectors.groupingBy(
                        PurchaseEntry::getPurchaseDate,
                        Collectors.summingDouble(entry -> safe(entry.getTotalAmount()))))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new SalesTrendPointDto(e.getKey().format(formatter), round(e.getValue())))
                .toList();
    }

    public List<PaymentModeSummaryDto> buildSupplierContribution(List<PurchaseEntry> purchases) {
        double total = purchases.stream().mapToDouble(entry -> safe(entry.getTotalAmount())).sum();
        if (total <= 0) return List.of();

        return purchases.stream()
                .collect(Collectors.groupingBy(
                        entry -> safeString(entry.getSupplierName(), entry.getSupplier() != null ? entry.getSupplier().getName() : "Unknown"),
                        Collectors.summingDouble(entry -> safe(entry.getTotalAmount()))))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new PaymentModeSummaryDto(e.getKey(), round((e.getValue() * 100.0) / total)))
                .toList();
    }

    public List<PaymentModeSummaryDto> buildPurchaseVsReturn(List<PurchaseEntry> purchases, List<PurchaseReturn> returns) {
        return List.of(
                new PaymentModeSummaryDto("Purchases", round(purchases.stream().mapToDouble(entry -> safe(entry.getTotalAmount())).sum())),
                new PaymentModeSummaryDto("Returns", round(returns.stream().mapToDouble(entry -> safe(entry.getTotalAmount())).sum()))
        );
    }

    public List<com.expygen.insights.dto.InsightsSummaryCardDto> buildKpis(Shop shop, List<PurchaseEntry> purchases, List<PurchaseReturn> returns) {
        double totalPurchases = purchases.stream().mapToDouble(entry -> safe(entry.getTotalAmount())).sum();
        long invoiceCount = purchases.size();
        double avgInvoice = invoiceCount > 0 ? totalPurchases / invoiceCount : 0;
        double returnValue = returns.stream().mapToDouble(entry -> safe(entry.getTotalAmount())).sum();
        int activeSuppliers = supplierRepository.findByShopAndActiveTrueOrderByNameAsc(shop).size();
        long totalItems = purchases.stream().mapToLong(entry -> entry.getBatches() != null ? entry.getBatches().size() : 0).sum();
        double netProcurement = totalPurchases - returnValue;

        List<com.expygen.insights.dto.InsightsSummaryCardDto> cards = new ArrayList<>();
        cards.add(card("Total Purchases", money(totalPurchases), "Procurement value for selected range"));
        cards.add(card("Purchase Invoices", String.valueOf(invoiceCount), "Total supplier invoices recorded"));
        cards.add(card("Average Invoice Value", money(avgInvoice), "Average purchase bill size"));
        cards.add(card("Net Procurement", money(netProcurement), "Purchases after supplier returns"));
        cards.add(card("Purchase Returns", money(returnValue), "Returned stock value impact"));
        cards.add(card("Active Suppliers", String.valueOf(activeSuppliers), "Suppliers with recorded purchase activity"));
        cards.add(card("Batch Lines", String.valueOf(totalItems), "Batch entries received in selected range"));
        return cards;
    }

    private com.expygen.insights.dto.InsightsSummaryCardDto card(String label, String value, String description) {
        return new com.expygen.insights.dto.InsightsSummaryCardDto(label, value, description);
    }

    private boolean matchesDate(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date == null) return false;
        if (fromDate != null && date.isBefore(fromDate)) return false;
        if (toDate != null && date.isAfter(toDate)) return false;
        return true;
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value != null && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double safe(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String safeString(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : safeString(fallback);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String money(double value) {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", round(value));
    }
}
