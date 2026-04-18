package com.expygen.insights.service;

import com.expygen.entity.Product;
import com.expygen.entity.Shop;
import com.expygen.insights.dto.DeadStockReportDto;
import com.expygen.insights.dto.DeadStockRowDto;
import com.expygen.insights.dto.InsightsSummaryCardDto;
import com.expygen.insights.dto.PaymentModeSummaryDto;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SaleItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeadStockInsightsService {

    private final ProductRepository productRepository;
    private final SaleItemRepository saleItemRepository;

    public DeadStockReportDto buildReport(Shop shop, Integer inactivityDays, String keyword, String manufacturer) {
        int effectiveDays = (inactivityDays == null || inactivityDays <= 0) ? 90 : inactivityDays;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(effectiveDays);

        List<Product> products = productRepository.findByShopAndActiveTrue(shop).stream()
                .filter(product -> safeInt(product.getStockQuantity()) > 0)
                .filter(product -> keyword == null || keyword.isBlank() || matchesKeyword(product, keyword))
                .filter(product -> manufacturer == null || manufacturer.isBlank()
                        || contains(product.getManufacturer(), manufacturer))
                .toList();

        Map<Long, LocalDateTime> lastSoldByProductId = saleItemRepository.findLastSoldAtByProduct(shop).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> (LocalDateTime) row[1]
                ));

        List<DeadStockRowDto> rows = products.stream()
                .map(product -> toRow(product, lastSoldByProductId.get(product.getId())))
                .filter(row -> row.getLastSoldAt() == null || row.getLastSoldAt().isBefore(cutoff))
                .sorted(Comparator
                        .comparing((DeadStockRowDto row) -> row.getDaysSinceLastSale() == null ? Long.MAX_VALUE : row.getDaysSinceLastSale())
                        .reversed()
                        .thenComparing(DeadStockRowDto::getProductName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return DeadStockReportDto.builder()
                .kpis(buildKpis(rows, effectiveDays))
                .manufacturerSplit(buildManufacturerSplit(rows))
                .stockAgeSplit(buildStockAgeSplit(rows))
                .rows(rows)
                .build();
    }

    private DeadStockRowDto toRow(Product product, LocalDateTime lastSoldAt) {
        int stock = safeInt(product.getStockQuantity());
        double purchasePrice = safe(product.getPurchasePrice());
        double salePrice = safe(product.getPrice());
        double stockValue = round((purchasePrice > 0 ? purchasePrice : salePrice) * stock);
        Long daysSinceLastSale = lastSoldAt == null ? null : ChronoUnit.DAYS.between(lastSoldAt, LocalDateTime.now());

        return DeadStockRowDto.builder()
                .productId(product.getId())
                .productName(safeString(product.getName(), "Unknown Product"))
                .manufacturer(safeString(product.getManufacturer(), "Unknown"))
                .barcode(safeString(product.getBarcode()))
                .currentStock(stock)
                .purchasePrice(round(purchasePrice))
                .salePrice(round(salePrice))
                .stockValue(stockValue)
                .lastSoldAt(lastSoldAt)
                .daysSinceLastSale(daysSinceLastSale)
                .status(lastSoldAt == null ? "Never Sold" : "Idle")
                .build();
    }

    private List<InsightsSummaryCardDto> buildKpis(List<DeadStockRowDto> rows, int inactivityDays) {
        long count = rows.size();
        double blockedValue = rows.stream().mapToDouble(DeadStockRowDto::getStockValue).sum();
        long neverSold = rows.stream().filter(row -> row.getLastSoldAt() == null).count();
        long units = rows.stream().mapToLong(row -> row.getCurrentStock() != null ? row.getCurrentStock() : 0).sum();
        long manufacturers = rows.stream().map(DeadStockRowDto::getManufacturer).distinct().count();
        long agedOver180 = rows.stream()
                .filter(row -> row.getDaysSinceLastSale() != null && row.getDaysSinceLastSale() >= 180)
                .count();

        return List.of(
                card("Dead Stock SKUs", String.valueOf(count), "Products inactive for " + inactivityDays + "+ days while stock is still on hand"),
                card("Blocked Inventory Value", money(blockedValue), "Estimated money currently locked in unsold stock"),
                card("Never Sold", String.valueOf(neverSold), "Products with stock but no completed sale yet"),
                card("Dead Stock Units", String.valueOf(units), "Total units currently sitting in dead stock"),
                card("Affected Manufacturers", String.valueOf(manufacturers), "Manufacturers contributing to idle stock"),
                card("180+ Day Ageing", String.valueOf(agedOver180), "Products idle for six months or more")
        );
    }

    private List<PaymentModeSummaryDto> buildManufacturerSplit(List<DeadStockRowDto> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> safeString(row.getManufacturer(), "Unknown"),
                        Collectors.summingDouble(row -> row.getStockValue() != null ? row.getStockValue() : 0.0)))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(entry -> new PaymentModeSummaryDto(entry.getKey(), round(entry.getValue())))
                .toList();
    }

    private List<PaymentModeSummaryDto> buildStockAgeSplit(List<DeadStockRowDto> rows) {
        return List.of(
                bucket("Never Sold", rows, row -> row.getLastSoldAt() == null),
                bucket("30-59 Days", rows, row -> inRange(row.getDaysSinceLastSale(), 30, 59)),
                bucket("60-89 Days", rows, row -> inRange(row.getDaysSinceLastSale(), 60, 89)),
                bucket("90-179 Days", rows, row -> inRange(row.getDaysSinceLastSale(), 90, 179)),
                bucket("180+ Days", rows, row -> row.getDaysSinceLastSale() != null && row.getDaysSinceLastSale() >= 180)
        );
    }

    private PaymentModeSummaryDto bucket(String label, List<DeadStockRowDto> rows, Function<DeadStockRowDto, Boolean> matcher) {
        double count = rows.stream().filter(row -> matcher.apply(row)).count();
        return new PaymentModeSummaryDto(label, count);
    }

    private boolean inRange(Long value, long start, long end) {
        return value != null && value >= start && value <= end;
    }

    private InsightsSummaryCardDto card(String label, String value, String description) {
        return new InsightsSummaryCardDto(label, value, description);
    }

    private boolean matchesKeyword(Product product, String keyword) {
        return contains(product.getName(), keyword)
                || contains(product.getGenericName(), keyword)
                || contains(product.getBarcode(), keyword)
                || contains(product.getManufacturer(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value != null
                && value.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safe(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String safeString(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String money(double value) {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", round(value));
    }
}
