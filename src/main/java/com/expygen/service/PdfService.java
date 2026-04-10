package com.expygen.service;

import com.expygen.config.AppConfig;
import com.expygen.entity.Sale;
import com.expygen.entity.SaleItem;
import com.expygen.entity.Shop;
import com.expygen.dto.SoldBatchTraceDTO;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.element.Image;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfService {

    private final AppConfig appConfig;
    private final SaleBatchTraceService saleBatchTraceService;

    private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb SECONDARY_COLOR = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb BRAND_ORANGE = new DeviceRgb(242, 122, 33);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(226, 232, 240);
    private static final DeviceRgb BACKGROUND_LIGHT = new DeviceRgb(248, 250, 252);

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(31, 41, 55);
    private static final DeviceRgb TEXT_LIGHT = new DeviceRgb(148, 163, 184);
    private static final DeviceRgb DIVIDER = new DeviceRgb(226, 232, 240);

    static {
        currencyFormat.setMaximumFractionDigits(2);
        currencyFormat.setMinimumFractionDigits(2);
    }

    public byte[] generateInvoicePdf(Sale sale) {
        log.info("========== START Premium PDF Generation for Sale: {} ==========", sale.getId());

        if (sale == null || sale.getShop() == null) {
            throw new RuntimeException("Invalid sale data");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.setMargins(20, 20, 20, 20);

            addPremiumHeader(document, sale);
            addInfoBoxes(document, sale);
            addPremiumItemsTable(document, sale);
            addPremiumSummary(document, sale);
            addPremiumFooter(document, sale);

            document.close();
            byte[] pdfBytes = baos.toByteArray();
            log.info("Premium PDF generated successfully, size: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Premium PDF generation failed", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private void addPremiumHeader(Document document, Sale sale) {
        Table header = new Table(UnitValue.createPercentArray(new float[] { 34, 32, 34 }));
        header.setWidth(UnitValue.createPercentValue(100));
        header.setMarginBottom(10);

        Cell spacerLeft = new Cell();
        spacerLeft.setBorder(Border.NO_BORDER);
        spacerLeft.setPadding(0);

        Cell brandCell = new Cell();
        brandCell.setBorder(Border.NO_BORDER);
        brandCell.setVerticalAlignment(VerticalAlignment.MIDDLE);
        brandCell.setHorizontalAlignment(HorizontalAlignment.CENTER);
        brandCell.setTextAlignment(TextAlignment.CENTER);
        brandCell.setPadding(0);
        brandCell.setBackgroundColor(new DeviceRgb(255, 255, 255));

        Cell spacerRight = new Cell();
        spacerRight.setBorder(Border.NO_BORDER);
        spacerRight.setPadding(0);

        try {
            Image logo = new Image(ImageDataFactory.create(
                    getClass().getResource("/static/images/expygen-logo-light.png")));

            logo.setWidth(80);
            logo.setAutoScaleHeight(true);
            logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
            brandCell.add(logo);

        } catch (Exception e) {
            Paragraph fallback = new Paragraph(appConfig.getAppName())
                    .setFontSize(18)
                    .setBold();
            fallback.setFontColor(PRIMARY_COLOR);
            fallback.setTextAlignment(TextAlignment.CENTER);
            brandCell.add(fallback);
        }

        header.addCell(spacerLeft);
        header.addCell(brandCell);
        header.addCell(spacerRight);
        document.add(header);

        Table accent = new Table(UnitValue.createPercentArray(new float[] { 14, 86 }));
        accent.setWidth(UnitValue.createPercentValue(100));
        accent.setMarginBottom(10);

        accent.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(BRAND_ORANGE).setHeight(5));
        accent.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(SECONDARY_COLOR).setHeight(5));

        document.add(accent);
    }

    private void addInvoiceMeta(Document document, Sale sale) {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 50, 50 }));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(10);

        Shop shop = sale.getShop();

        Cell left = new Cell();
        left.setBorder(new SolidBorder(BORDER_COLOR, 0.8f));
        left.setBackgroundColor(BACKGROUND_LIGHT);
        left.setPadding(8);

        left.add(new Paragraph("INVOICE DETAILS")
                .setFontSize(9)
                .setBold()
                .setFontColor(SECONDARY_COLOR)
                .setMarginBottom(4));

        left.add(new Paragraph("Invoice #: " + sale.getId())
                .setFontSize(8)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(2));

        String date = sale.getSaleDate() != null
                ? sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "N/A";

        left.add(new Paragraph("Date & Time:  " + date)
                .setFontSize(8)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(0));

        Cell right = new Cell();
        right.setBorder(new SolidBorder(BORDER_COLOR, 0.8f));
        right.setBackgroundColor(new DeviceRgb(255, 255, 255));
        right.setTextAlignment(TextAlignment.RIGHT);
        right.setPadding(8);

        right.add(new Paragraph(shop.getName())
                .setFontSize(10)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setMarginBottom(3));

        right.add(new Paragraph(
                (shop.getAddress() != null ? shop.getAddress() : "") +
                        (shop.getCity() != null ? ", " + shop.getCity() : ""))
                .setFontSize(7)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(2));

        right.add(new Paragraph(
                (shop.getState() != null ? shop.getState() : "") +
                        (shop.getPincode() != null ? " - " + shop.getPincode() : ""))
                .setFontSize(7)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(2));

        right.add(new Paragraph("GSTIN: " + (shop.getGstNumber() != null ? shop.getGstNumber() : "N/A"))
                .setFontSize(7)
                .setFontColor(BRAND_ORANGE)
                .setBold()
                .setMarginBottom(0));

        table.addCell(left);
        table.addCell(right);

        document.add(table);
    }

    private void addInfoBoxes(Document document, Sale sale) {
        Shop shop = sale.getShop();

        Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 50, 50 }));
        infoTable.setWidth(UnitValue.createPercentValue(100));
        infoTable.setMarginBottom(10);

        Cell sellerBox = new Cell();
        sellerBox.setBackgroundColor(BACKGROUND_LIGHT);
        sellerBox.setBorder(new SolidBorder(BORDER_COLOR, 0.8f));
        sellerBox.setPadding(8);

        sellerBox.add(new Paragraph("BILL FROM")
                .setFontSize(9)
                .setBold()
                .setFontColor(SECONDARY_COLOR)
                .setMarginBottom(4));
        sellerBox.add(new Paragraph(shop.getName() != null ? shop.getName() : "N/A")
                .setFontSize(9)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setMarginBottom(3));

        sellerBox.add(new Paragraph(
                (shop.getAddress() != null ? shop.getAddress() : "") +
                        (shop.getCity() != null ? ", " + shop.getCity() : ""))
                .setFontSize(8)
                .setFontColor(new DeviceRgb(71, 85, 105))
                .setMarginBottom(2));

        sellerBox.add(new Paragraph(
                (shop.getState() != null ? shop.getState() : "") +
                        (shop.getPincode() != null ? " - " + shop.getPincode() : ""))
                .setFontSize(8)
                .setFontColor(new DeviceRgb(71, 85, 105))
                .setMarginBottom(2));

        sellerBox.add(new Paragraph("GST: " + (shop.getGstNumber() != null ? shop.getGstNumber() : "N/A"))
                .setFontSize(8)
                .setFontColor(BRAND_ORANGE)
                .setBold()
                .setMarginBottom(0));

        Cell customerBox = new Cell();
        customerBox.setBackgroundColor(new DeviceRgb(255, 255, 255));
        customerBox.setBorder(new SolidBorder(BORDER_COLOR, 0.8f));
        customerBox.setPadding(8);

        customerBox.add(new Paragraph("BILL TO")
                .setFontSize(9)
                .setBold()
                .setFontColor(SECONDARY_COLOR)
                .setMarginBottom(4));
        customerBox.add(new Paragraph("Name  : " + (sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in Customer"))
                .setFontSize(9)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setMarginBottom(3));
        customerBox.add(new Paragraph("Phone : " + (sale.getCustomerPhone() != null ? sale.getCustomerPhone() : "—"))
                .setFontSize(8)
                .setFontColor(new DeviceRgb(71, 85, 105))
                .setMarginBottom(2));

        infoTable.addCell(sellerBox);
        infoTable.addCell(customerBox);
        addInvoiceMeta(document, sale);
        document.add(infoTable);
    }

    private void addPremiumItemsTable(Document document, Sale sale) {
        List<SaleItem> items = sale.getItems();
        Map<Long, List<SoldBatchTraceDTO>> batchTraceBySaleItemId = saleBatchTraceService.getBatchTraceBySaleItem(items);

        if (items == null || items.isEmpty()) {
            document.add(new Paragraph("No items in this invoice")
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));
            return;
        }

        float[] columnWidths = { 5, 35, 12, 8, 8, 12, 12, 8 };
        Table table = new Table(UnitValue.createPercentArray(columnWidths));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(8);

        String[] headers = { "SL", "DESCRIPTION", "PRICE", "QTY", "GST", "CGST", "SGST", "TOTAL" };

        for (String header : headers) {
            Cell headerCell = new Cell()
                    .add(new Paragraph(header)
                            .setFontSize(8)
                            .setBold()
                            .setFontColor(new DeviceRgb(255, 255, 255)))
                    .setBackgroundColor(SECONDARY_COLOR)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(6)
                    .setTextAlignment(TextAlignment.CENTER);

            table.addCell(headerCell);
        }

        int index = 1;
        for (SaleItem item : items) {
            boolean evenRow = index % 2 == 0;
            DeviceRgb rowColor = evenRow ? new DeviceRgb(249, 250, 251) : new DeviceRgb(255, 255, 255);
            if (item.getProduct() == null)
                continue;

            double price = item.getPriceAtSale() != null ? item.getPriceAtSale().doubleValue() : 0;
            int quantity = item.getQuantity();
            double gstPercent = item.getGstPercent() != null ? item.getGstPercent() : 0;
            double basePrice = item.getSubtotal() != null ? item.getSubtotal().doubleValue() : price * quantity;
            double gstAmount = item.getGstAmount() != null ? item.getGstAmount().doubleValue()
                    : (basePrice * gstPercent / 100);
            double cgst = gstAmount / 2;
            double sgst = gstAmount / 2;
            double totalWithGst = item.getTotalWithGst() != null ? item.getTotalWithGst().doubleValue()
                    : (basePrice + gstAmount);

            table.addCell(createCell(String.valueOf(index++), 7, TextAlignment.CENTER, rowColor));
            table.addCell(createDescriptionCell(item, batchTraceBySaleItemId.get(item.getId()), rowColor));
            table.addCell(createCell(currencyFormat.format(price), 7, TextAlignment.RIGHT, rowColor));
            table.addCell(createCell(String.valueOf(quantity), 7, TextAlignment.CENTER, rowColor));
            table.addCell(createCell(String.format("%.0f%%", gstPercent), 7, TextAlignment.CENTER, rowColor));
            table.addCell(createCell(currencyFormat.format(cgst), 7, TextAlignment.RIGHT, rowColor));
            table.addCell(createCell(currencyFormat.format(sgst), 7, TextAlignment.RIGHT, rowColor));
            table.addCell(createCell(currencyFormat.format(totalWithGst), 7, TextAlignment.RIGHT, rowColor));
        }

        document.add(table);

        Paragraph bottomLine = new Paragraph(" ")
                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
                .setMarginTop(3)
                .setMarginBottom(5);
        document.add(bottomLine);
    }

    private void addPremiumSummary(Document document, Sale sale) {
        double subtotal = 0;
        double totalGst = 0;

        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() != null) {
                double price = item.getPriceAtSale() != null ? item.getPriceAtSale().doubleValue() : 0;
                int quantity = item.getQuantity();
                double basePrice = item.getSubtotal() != null ? item.getSubtotal().doubleValue() : price * quantity;
                subtotal += basePrice;

                double gstAmount = item.getGstAmount() != null ? item.getGstAmount().doubleValue()
                        : (basePrice * (item.getGstPercent() != null ? item.getGstPercent() : 0) / 100);
                totalGst += gstAmount;
            }
        }

        double cgst = totalGst / 2;
        double sgst = totalGst / 2;
        double totalAmount = subtotal + totalGst;

        double discountAmount = 0;
        double discountPercent = 0;
        if (sale.getDiscountAmount() != null && sale.getDiscountAmount().doubleValue() > 0) {
            discountAmount = sale.getDiscountAmount().doubleValue();
            if (sale.getDiscountPercent() != null) {
                discountPercent = sale.getDiscountPercent().doubleValue();
            }
            totalAmount = totalAmount - discountAmount;
        }

        // Create a 2-column layout: 40% for seal/signature, 60% for summary
        Table mainTable = new Table(UnitValue.createPercentArray(new float[] { 34, 66 }));
        mainTable.setWidth(UnitValue.createPercentValue(100));
        mainTable.setMarginTop(5);
        mainTable.setMarginBottom(3);

        // Left cell for seal and signature
        Cell sealCell = new Cell();
        sealCell.setBorder(Border.NO_BORDER);
        sealCell.setVerticalAlignment(VerticalAlignment.TOP);
        sealCell.setHorizontalAlignment(HorizontalAlignment.CENTER);
        sealCell.setPaddingTop(0);
        sealCell.setPaddingRight(2);
        sealCell.setPaddingBottom(0);
        sealCell.setPaddingLeft(2);
        sealCell.setMaxHeight(78);

        try {
            Image sealSignature = new Image(ImageDataFactory.create(
                    getClass().getResource("/static/images/seal_signature.png")));

            sealSignature.setWidth(84);
            sealSignature.setHorizontalAlignment(HorizontalAlignment.CENTER);
            sealSignature.setAutoScale(true);
            sealSignature.setMarginTop(0);

            sealCell.add(sealSignature);

        } catch (Exception e) {
            log.warn("Seal/signature image not found");
            // Optional fallback text
            Paragraph fallback = new Paragraph("Authorized Signature\nCompany Seal")
                    .setFontSize(8)
                    .setFontColor(TEXT_LIGHT)
                    .setTextAlignment(TextAlignment.CENTER);
            sealCell.add(fallback);
        }

        // Right cell for summary table
        Cell summaryCell = new Cell();
        summaryCell.setBorder(Border.NO_BORDER);
        summaryCell.setPadding(0);

        Table summaryTable = new Table(UnitValue.createPercentArray(new float[] { 60, 40 }));
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        summaryTable.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
        summaryTable.setBorder(new SolidBorder(BORDER_COLOR, 1));
        summaryTable.setPadding(8);
        summaryTable.setBackgroundColor(new DeviceRgb(255, 255, 255));

        summaryTable.addCell(createEmptyCell());
        summaryTable.addCell(createEmptyCell());

        summaryTable.addCell(createSummaryLabel("Subtotal"));
        summaryTable.addCell(createSummaryValue(currencyFormat.format(subtotal)));

        summaryTable.addCell(createSummaryLabel("CGST"));
        summaryTable.addCell(createSummaryValue(currencyFormat.format(cgst)));

        summaryTable.addCell(createSummaryLabel("SGST"));
        summaryTable.addCell(createSummaryValue(currencyFormat.format(sgst)));

        summaryTable.addCell(createSummaryLabel("Total Tax"));
        summaryTable.addCell(createSummaryValue(currencyFormat.format(totalGst)));

        if (discountAmount > 0) {
            summaryTable.addCell(createSummaryLabel("Discount"));
            summaryTable.addCell(createSummaryValue("- " + currencyFormat.format(discountAmount) + " ("
                    + String.format("%.0f", discountPercent) + "%)"));
        }

        summaryTable.addCell(createSeparatorCell());
        summaryTable.addCell(createSeparatorCell());

        summaryTable.addCell(createSummaryLabel("GRAND TOTAL", true));
        summaryTable.addCell(createSummaryValue(currencyFormat.format(totalAmount), true));

        summaryCell.add(summaryTable);

        mainTable.addCell(sealCell);
        mainTable.addCell(summaryCell);

        document.add(mainTable);

        addPaymentDetailsBox(document, sale);
    }

    private void addPaymentDetailsBox(Document document, Sale sale) {
        Table paymentTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1, 1, 1 }));
        paymentTable.setWidth(UnitValue.createPercentValue(100));
        paymentTable.setMarginTop(1);
        paymentTable.setMarginBottom(8);

        String[] labels = { "PAYMENT MODE", "AMOUNT RECEIVED", "CHANGE", "STATUS" };
        String[] values = {
                sale.getPaymentMode() != null ? sale.getPaymentMode() : "Cash",
                currencyFormat.format(sale.getAmountReceived() != null ? sale.getAmountReceived().doubleValue() : 0),
                currencyFormat.format(sale.getChangeReturned() != null ? sale.getChangeReturned().doubleValue() : 0),
                "PAID"
        };

        for (int i = 0; i < labels.length; i++) {
            Cell cell = new Cell();
            cell.setBackgroundColor(i == 3 ? BACKGROUND_LIGHT : new DeviceRgb(255, 255, 255));
            cell.setBorder(new SolidBorder(BORDER_COLOR, 0.8f));
            cell.setPadding(7);
            cell.add(new Paragraph(labels[i])
                    .setFontSize(7)
                    .setFontColor(new DeviceRgb(71, 85, 105))
                    .setBold());
            cell.add(new Paragraph(values[i])
                    .setFontSize(9)
                    .setBold()
                    .setFontColor(i == 3 ? BRAND_ORANGE : PRIMARY_COLOR));
            paymentTable.addCell(cell);
        }

        document.add(paymentTable);
    }

    private void addPremiumFooter(Document document, Sale sale) {
        Paragraph line = new Paragraph("")
                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
                .setMarginTop(10)
                .setMarginBottom(5);

        document.add(line);

        Paragraph thankYou = new Paragraph("Thank you for your business")
                .setFontSize(9)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);

        document.add(thankYou);

        Paragraph footer = new Paragraph(
                "This invoice was generated by " + appConfig.getAppName() + " • " + appConfig.getAppTagline())
                .setFontSize(7)
                .setFontColor(TEXT_LIGHT)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);

        document.add(footer);

        Paragraph website = new Paragraph(getAppBaseUrl())
                .setFontSize(6)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(new DeviceRgb(148, 163, 184));

        document.add(website);
    }

    // Helper methods with compact padding
    private Cell createCell(String content, float fontSize, TextAlignment alignment, DeviceRgb backgroundColor) {
        Cell cell = new Cell();
        cell.add(new Paragraph(content).setFontSize(fontSize));
        cell.setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
        cell.setPadding(4);
        cell.setTextAlignment(alignment);
        if (backgroundColor != null) {
            cell.setBackgroundColor(backgroundColor);
        }
        return cell;
    }

    private Cell createDescriptionCell(SaleItem item, List<SoldBatchTraceDTO> soldBatchTrace, DeviceRgb backgroundColor) {
        Cell cell = new Cell();
        cell.setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
        cell.setPadding(4);
        cell.setTextAlignment(TextAlignment.LEFT);
        if (backgroundColor != null) {
            cell.setBackgroundColor(backgroundColor);
        }

        cell.add(new Paragraph(item.getProduct().getName() != null ? item.getProduct().getName() : "Unknown")
                .setFontSize(7)
                .setBold());

        List<SoldBatchTraceDTO> safeTrace = soldBatchTrace != null ? soldBatchTrace : Collections.emptyList();
        safeTrace.stream()
                .sorted(Comparator.comparing(SoldBatchTraceDTO::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(batch -> cell.add(new Paragraph(buildBatchTraceLine(batch))
                        .setFontSize(6)
                        .setFontColor(batch.isExpired() ? new DeviceRgb(185, 28, 28) : SECONDARY_COLOR)
                        .setMarginTop(1)
                        .setMarginBottom(0)));

        return cell;
    }

    private Cell createCell(String content, float fontSize) {
        return createCell(content, fontSize, TextAlignment.LEFT, null);
    }

    private Cell createCell(String content, float fontSize, TextAlignment alignment) {
        return createCell(content, fontSize, alignment, null);
    }

    private String buildBatchTraceLine(SoldBatchTraceDTO batch) {
        StringBuilder builder = new StringBuilder();
        builder.append(batch.getBatchNumber() != null ? batch.getBatchNumber() : "Batch");
        builder.append(" • Qty ").append(batch.getQuantity());
        LocalDate expiryDate = batch.getExpiryDate();
        if (expiryDate != null) {
            builder.append(" • Exp ").append(expiryDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        }
        return builder.toString();
    }

    private Cell createSummaryLabel(String label) {
        return createSummaryLabel(label, false);
    }

    private Cell createSummaryLabel(String label, boolean bold) {
        Cell cell = new Cell();
        Paragraph p = new Paragraph(label).setFontSize(8);
        if (bold)
            p.setBold();
        cell.add(p);
        cell.setBorder(Border.NO_BORDER);
        cell.setPadding(2);
        cell.setTextAlignment(TextAlignment.RIGHT);
        return cell;
    }

    private Cell createSummaryValue(String value) {
        return createSummaryValue(value, false);
    }

    private Cell createSummaryValue(String value, boolean bold) {
        Cell cell = new Cell();
        Paragraph p = new Paragraph(value).setFontSize(8);
        if (bold) {
            p.setBold().setFontColor(SECONDARY_COLOR);
        }
        cell.add(p);
        cell.setBorder(Border.NO_BORDER);
        cell.setPadding(2);
        cell.setTextAlignment(TextAlignment.RIGHT);
        return cell;
    }

    private Cell createEmptyCell() {
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.setPadding(1);
        return cell;
    }

    private Cell createSeparatorCell() {
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.setPadding(0);
        cell.setHeight(0.5f);
        cell.setBackgroundColor(BORDER_COLOR);
        return cell;
    }

    private String getAppBaseUrl() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(
                    RequestContextHolder.getRequestAttributes())).getRequest();
            String url = request.getRequestURL().toString()
                    .replace(request.getServletPath(), "");
            return url;
        } catch (Exception e) {
            log.error("Could not get request URL", e);
            return "http://localhost:8080";
        }
    }
}
