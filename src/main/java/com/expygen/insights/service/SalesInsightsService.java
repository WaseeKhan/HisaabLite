package com.expygen.insights.service;

import com.expygen.entity.Sale;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.insights.dto.SalesInvoiceSummaryRowDto;
import com.expygen.insights.dto.SalesSummaryFilterRequest;
import com.expygen.insights.dto.SalesSummaryKpiDto;
import com.expygen.insights.dto.SalesSummaryPageDto;
import com.expygen.insights.dto.SalesTrendPointDto;
import com.expygen.insights.dto.TopProductSummaryDto;
import com.expygen.repository.SaleItemRepository;
import com.expygen.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import com.expygen.entity.SaleItem;

@Service
@RequiredArgsConstructor
public class SalesInsightsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;

    public SalesSummaryPageDto getSalesSummary(SalesSummaryFilterRequest filter, Shop shop) {
        List<Sale> sales = findSales(filter, shop);

        SalesSummaryPageDto page = new SalesSummaryPageDto();
        page.setKpis(buildKpis(sales));
        page.setSalesTrend(buildTrend(sales));
        page.setInvoices(buildInvoiceRows(sales));
        page.setPaymentModes(buildPaymentModeSplit(sales));
        page.setTopProducts(buildTopProducts(sales));

        return page;
    }

    public List<Sale> findSales(SalesSummaryFilterRequest filter, Shop shop) {
        LocalDate fromDate = filter.getFromDate();
        LocalDate toDate = filter.getToDate();

        if (fromDate == null && toDate == null) {
            fromDate = LocalDate.now().withDayOfMonth(1);
            toDate = LocalDate.now();
        } else if (fromDate == null) {
            fromDate = toDate;
        } else if (toDate == null) {
            toDate = fromDate;
        }

        List<Sale> sales = saleRepository.findByShopAndSaleDateBetweenOrderBySaleDateDesc(
                shop,
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay());

        return applySalesFilters(sales, filter);
    }

    private List<Sale> applySalesFilters(List<Sale> sales, SalesSummaryFilterRequest filter) {
        return sales.stream()
                .filter(sale -> {
                    if (filter.getPaymentMode() == null || filter.getPaymentMode().isBlank()) {
                        return true;
                    }
                    return sale.getPaymentMode() != null
                            && sale.getPaymentMode().equalsIgnoreCase(filter.getPaymentMode());
                })
                .filter(sale -> {
                    if (filter.getKeyword() == null || filter.getKeyword().isBlank()) {
                        return true;
                    }
                    String keyword = filter.getKeyword().toLowerCase();

                    return contains(String.valueOf(sale.getId()), keyword)
                            || contains(sale.getCustomerName(), keyword)
                            || contains(sale.getCustomerPhone(), keyword)
                            || contains(sale.getPaymentMode(), keyword)
                            || contains(sale.getStatus() != null ? sale.getStatus().name() : null, keyword);
                })
                .sorted(Comparator.comparing(Sale::getSaleDate).reversed())
                .toList();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private SalesSummaryKpiDto buildKpis(List<Sale> sales) {
        BigDecimal totalSales = sales.stream()
                .filter(this::isCompleted)
                .map(Sale::getTotalAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long invoiceCount = sales.stream()
                .filter(this::isCompleted)
                .count();

        BigDecimal gstCollected = sales.stream()
                .filter(this::isCompleted)
                .map(Sale::getTotalGstAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = sales.stream()
                .filter(this::isCompleted)
                .map(Sale::getDiscountAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long cancelledBills = sales.stream()
                .filter(s -> s.getStatus() == SaleStatus.CANCELLED)
                .count();

        double avgBillValue = invoiceCount > 0
                ? totalSales.doubleValue() / invoiceCount
                : 0.0;

        return new SalesSummaryKpiDto(
                round(totalSales.doubleValue()),
                invoiceCount,
                round(avgBillValue),
                round(gstCollected.doubleValue()),
                round(totalDiscount.doubleValue()),
                cancelledBills);
    }

    private List<SalesTrendPointDto> buildTrend(List<Sale> sales) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM");

        return sales.stream()
                .filter(this::isCompleted)
                .filter(s -> s.getSaleDate() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getSaleDate().toLocalDate(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                s -> s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)))
                .entrySet()
                .stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new SalesTrendPointDto(
                        entry.getKey().format(formatter),
                        round(entry.getValue().doubleValue())))
                .toList();
    }

    public List<SalesInvoiceSummaryRowDto> buildInvoiceRows(List<Sale> sales) {
        return sales.stream()
                .map(sale -> new SalesInvoiceSummaryRowDto(
                        sale.getId(),
                        "SALE-" + sale.getId(),
                        sale.getSaleDate(),
                        sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in Customer",
                        sale.getCustomerPhone() != null ? sale.getCustomerPhone() : "-",
                        sale.getPaymentMode(),
                        safe(sale.getTaxableAmount()),
                        safe(sale.getTotalGstAmount()),
                        safe(sale.getDiscountAmount()),
                        safe(sale.getTotalAmount()),
                        sale.getStatus() != null ? sale.getStatus().name() : ""))
                .toList();
    }

    private boolean isCompleted(Sale sale) {
        return sale.getStatus() == null || sale.getStatus() == SaleStatus.COMPLETED;
    }

    private double safe(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<PaymentModeSummaryDto> buildPaymentModeSplit(List<Sale> sales) {
        return sales.stream()
                .filter(this::isCompleted)
                .collect(Collectors.groupingBy(
                        sale -> {
                            if (sale.getPaymentMode() == null || sale.getPaymentMode().isBlank()) {
                                return "UNKNOWN";
                            }
                            return sale.getPaymentMode().toUpperCase();
                        },
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                sale -> sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)))
                .entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> new PaymentModeSummaryDto(
                        entry.getKey(),
                        round(entry.getValue().doubleValue())))
                .toList();
    }



    private List<TopProductSummaryDto> buildTopProducts(List<Sale> sales) {
    List<Sale> completedSales = sales.stream()
            .filter(this::isCompleted)
            .toList();

    if (completedSales.isEmpty()) {
        return List.of();
    }

    List<SaleItem> saleItems = saleItemRepository.findBySaleIn(completedSales);

    return saleItems.stream()
        .filter(item -> item.getProduct() != null)
        .collect(Collectors.groupingBy(
                item -> item.getProduct().getName(),
                Collectors.reducing(
                        BigDecimal.ZERO,
                        item -> item.getTotalWithGst() != null ? item.getTotalWithGst() : BigDecimal.ZERO,
                        BigDecimal::add
                )
        ))
        .entrySet()
        .stream()
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .limit(5)
        .map(entry -> new TopProductSummaryDto(
                entry.getKey(),
                round(entry.getValue().doubleValue())
        ))
        .toList();
}
    
}
