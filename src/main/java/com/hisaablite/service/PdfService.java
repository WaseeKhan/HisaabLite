package com.hisaablite.service;

import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.repository.SaleItemRepository;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfService {

    private final SaleItemRepository saleItemRepository;

    private String getAppBaseUrl() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(
                    RequestContextHolder.getRequestAttributes())).getRequest();
            return request.getRequestURL().toString()
                    .replace(request.getServletPath(), "");
        } catch (Exception e) {
            log.error("Could not get request URL in PdfService", e);
            return "http://localhost:8080"; // fallback
        }
    }

    public byte[] generateInvoicePdf(Sale sale) {
        log.info("========== START PDF Generation for Sale: {} ==========", sale.getId());

        // NULL CHECKS - CRITICAL
        if (sale == null) {
            log.error("Sale object is NULL");
            throw new RuntimeException("Sale cannot be null");
        }

        if (sale.getShop() == null) {
            log.error("Shop is NULL for sale: {}", sale.getId());
            throw new RuntimeException("Shop cannot be null for sale: " + sale.getId());
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            addHeader(document, sale);
            addCustomerInfo(document, sale);
            addItemsTable(document, sale);
            addSummary(document, sale);
            addFooter(document, sale);

            document.close();
            byte[] pdfBytes = baos.toByteArray();
            log.info("PDF generated successfully, size: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("PDF generation failed for sale: {}", sale.getId(), e);
            throw new RuntimeException("Failed to generate PDF for sale: " + sale.getId(), e);
        }
    }

    private void addHeader(Document document, Sale sale) {
        try {
            document.add(new Paragraph("HisaabLite")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("Retail Billing Solution")
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            document.add(new Paragraph("INVOICE #" + sale.getId())
                    .setFontSize(16)
                    .setBold()
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginBottom(20));
        } catch (Exception e) {
            log.error("Error in addHeader for sale: {}", sale.getId(), e);
            throw e;
        }
    }

    private void addCustomerInfo(Document document, Sale sale) {
        try {
            // Shop details with null checks
            String shopName = sale.getShop().getName();
            String shopAddress = sale.getShop().getAddress();
            String gstNumber = sale.getShop().getGstNumber();

            document.add(new Paragraph("SELLER:").setFontSize(10).setBold());
            document.add(new Paragraph(shopName != null ? shopName : "N/A").setFontSize(10));
            document.add(new Paragraph(shopAddress != null ? shopAddress : "N/A").setFontSize(10));
            document.add(new Paragraph("GST: " + (gstNumber != null ? gstNumber : "N/A"))
                    .setFontSize(10)
                    .setMarginBottom(10));

            // Customer details with null checks
            String customerName = sale.getCustomerName();
            String customerPhone = sale.getCustomerPhone();

            document.add(new Paragraph("CUSTOMER:").setFontSize(10).setBold());
            document.add(new Paragraph(customerName != null ? customerName : "Walk-in Customer")
                    .setFontSize(10));
            document.add(new Paragraph("Phone: " + (customerPhone != null ? customerPhone : "-"))
                    .setFontSize(10));

            // Date with null check
            String dateStr = sale.getSaleDate() != null
                    ? sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a"))
                    : "N/A";
            document.add(new Paragraph("Date: " + dateStr)
                    .setFontSize(10)
                    .setMarginBottom(20));

        } catch (Exception e) {
            log.error("Error in addCustomerInfo for sale: {}", sale.getId(), e);
            throw e;
        }
    }

    private void addItemsTable(Document document, Sale sale) {
        try {
            List<SaleItem> items = saleItemRepository.findBySale(sale);

            if (items == null || items.isEmpty()) {
                log.warn("No items found for sale: {}", sale.getId());
                document.add(new Paragraph("No items in this sale")
                        .setFontSize(10)
                        .setMarginBottom(20));
                return;
            }

            Table table = new Table(new float[] { 1, 3, 1, 1, 2 });
            table.setWidth(500);

            // Headers
            table.addHeaderCell(new Cell().add(new Paragraph("#").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Product").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Price").setBold()).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(new Cell().add(new Paragraph("Qty").setBold()).setTextAlignment(TextAlignment.CENTER));
            table.addHeaderCell(new Cell().add(new Paragraph("Total").setBold()).setTextAlignment(TextAlignment.RIGHT));

            int index = 1;
            for (SaleItem item : items) {
                if (item.getProduct() == null) {
                    log.warn("Item {} has null product for sale: {}", index, sale.getId());
                    continue;
                }

                table.addCell(new Cell().add(new Paragraph(String.valueOf(index++))));
                table.addCell(new Cell().add(new Paragraph(item.getProduct().getName() != null
                        ? item.getProduct().getName()
                        : "Unknown")));
                table.addCell(new Cell().add(new Paragraph("Rs" + (item.getProduct().getPrice() != null
                        ? item.getProduct().getPrice()
                        : "0"))).setTextAlignment(TextAlignment.RIGHT));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(item.getQuantity())))
                        .setTextAlignment(TextAlignment.CENTER));
                table.addCell(new Cell().add(new Paragraph("Rs" + (item.getSubtotal() != null
                        ? item.getSubtotal()
                        : "0"))).setTextAlignment(TextAlignment.RIGHT));
            }

            document.add(table.setMarginBottom(20));

        } catch (Exception e) {
            log.error("Error in addItemsTable for sale: {}", sale.getId(), e);
            throw e;
        }
    }

    private void addSummary(Document document, Sale sale) {
        try {
            Table table = new Table(new float[] { 3, 1 });
            table.setWidth(300);
            table.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);

            // Subtotal
            table.addCell(new Cell().add(new Paragraph("Subtotal:")));
            table.addCell(new Cell().add(new Paragraph("Rs" + (sale.getTotalAmount() != null
                    ? sale.getTotalAmount()
                    : "0"))).setTextAlignment(TextAlignment.RIGHT));

            // GST
            if (sale.getTotalGstAmount() != null && sale.getTotalGstAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                table.addCell(new Cell().add(new Paragraph("GST:")));
                table.addCell(new Cell().add(new Paragraph("Rs" + sale.getTotalGstAmount()))
                        .setTextAlignment(TextAlignment.RIGHT));
            }

            // Discount
            if (sale.getDiscountAmount() != null && sale.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                table.addCell(new Cell().add(new Paragraph("Discount:")));
                table.addCell(new Cell().add(new Paragraph("-Rs" + sale.getDiscountAmount()))
                        .setTextAlignment(TextAlignment.RIGHT));
            }

            // Total
            table.addCell(new Cell().add(new Paragraph("TOTAL:").setBold()));
            table.addCell(new Cell().add(new Paragraph("Rs" + (sale.getTotalAmount() != null
                    ? sale.getTotalAmount()
                    : "0")).setBold()).setTextAlignment(TextAlignment.RIGHT));

            document.add(table.setMarginBottom(20));

            // Payment info
            document.add(
                    new Paragraph("Payment Mode: " + (sale.getPaymentMode() != null ? sale.getPaymentMode() : "Cash"))
                            .setFontSize(10));
            document.add(new Paragraph("Amount Received: Rs" + (sale.getAmountReceived() != null
                    ? sale.getAmountReceived()
                    : "0")).setFontSize(10));
            document.add(new Paragraph("Change Returned: Rs" + (sale.getChangeReturned() != null
                    ? sale.getChangeReturned()
                    : "0")).setFontSize(10).setMarginBottom(20));

        } catch (Exception e) {
            log.error("Error in addSummary for sale: {}", sale.getId(), e);
            throw e;
        }
    }

    private void addFooter(Document document, Sale sale) {
        try {
            String appUrl = getAppBaseUrl();
            String invoiceLink = appUrl + "/sales/invoice/" + sale.getId();

            document.add(new Paragraph("Thank you for your business!")
                    .setFontSize(12)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20));

            document.add(new Paragraph("View online: " + invoiceLink)
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("This is a computer generated invoice - no signature required")
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(10));

        } catch (Exception e) {
            log.error("Error in addFooter for sale: {}", sale.getId(), e);
            throw e;
        }
    }
}