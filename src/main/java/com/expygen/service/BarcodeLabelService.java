package com.expygen.service;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.springframework.stereotype.Service;

import com.expygen.config.AppConfig;
import com.expygen.entity.Product;
import com.itextpdf.barcodes.Barcode128;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BarcodeLabelService {

    public enum LabelSheetSize {
        SHEET_20(20, "20 per sheet"),
        SHEET_24(24, "24 per sheet"),
        SHEET_40(40, "40 per sheet"),
        SHEET_48(48, "48 per sheet"),
        SHEET_65(65, "65 per sheet"),
        SHEET_80(80, "80 per sheet");

        private final int labelsPerSheet;
        private final String label;

        LabelSheetSize(int labelsPerSheet, String label) {
            this.labelsPerSheet = labelsPerSheet;
            this.label = label;
        }

        public int getLabelsPerSheet() {
            return labelsPerSheet;
        }

        public String getLabel() {
            return label;
        }

        public static LabelSheetSize fromInput(String value) {
            if (value == null || value.isBlank()) {
                return SHEET_40;
            }

            String normalized = value.trim().toUpperCase();
            return switch (normalized) {
                case "SMALL", "SHEET_40", "40", "40_PER_SHEET" -> SHEET_40;
                case "MEDIUM", "SHEET_24", "24", "24_PER_SHEET" -> SHEET_24;
                case "LARGE", "SHEET_20", "20", "20_PER_SHEET" -> SHEET_20;
                case "SHEET_48", "48", "48_PER_SHEET" -> SHEET_48;
                case "SHEET_65", "65", "65_PER_SHEET" -> SHEET_65;
                case "SHEET_80", "80", "80_PER_SHEET" -> SHEET_80;
                default -> throw new IllegalArgumentException("Unknown label sheet preset");
            };
        }
    }

    private static final DeviceRgb LABEL_BORDER = new DeviceRgb(226, 232, 240);
    private static final DeviceRgb LABEL_ACCENT = new DeviceRgb(47, 116, 218);
    private static final DeviceRgb LABEL_TEXT = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb LABEL_MUTED = new DeviceRgb(100, 116, 139);

    private final AppConfig appConfig;

    public byte[] generateProductLabel(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Product is required to generate a barcode label");
        }
        if (product.getBarcode() == null || product.getBarcode().isBlank()) {
            throw new IllegalArgumentException("Barcode is required before printing a label");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(new PageSize(340, 250));

            Document document = new Document(pdf);
            document.setMargins(10, 14, 10, 14);

            document.add(new Paragraph(appConfig.getAppName())
                    .setFontSize(8)
                    .setBold()
                    .setFontColor(LABEL_ACCENT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(3));

            document.add(new Paragraph(product.getName())
                    .setFontSize(12)
                    .setBold()
                    .setFontColor(LABEL_TEXT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(2));

            String metaLine = buildMetaLine(product);
            if (!metaLine.isBlank()) {
                document.add(new Paragraph(metaLine)
                        .setFontSize(7)
                        .setFontColor(LABEL_MUTED)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(6));
            }

            Barcode128 barcode = new Barcode128(pdf);
            barcode.setCodeType(Barcode128.CODE128);
            barcode.setCode(product.getBarcode().trim());
            barcode.setFont(null);
            barcode.setBarHeight(30f);
            barcode.setX(1f);

            Image barcodeImage = new Image(barcode.createFormXObject(LABEL_TEXT, LABEL_TEXT, pdf));
            barcodeImage.setAutoScale(true);
            barcodeImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            barcodeImage.setMarginBottom(3);
            document.add(barcodeImage);

            document.add(new Paragraph(product.getBarcode().trim())
                    .setFontSize(8)
                    .setBold()
                    .setFontColor(LABEL_TEXT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5));

            String priceLine = buildPriceLine(product);
            if (!priceLine.isBlank()) {
                document.add(new Paragraph(priceLine)
                        .setFontSize(7)
                        .setFontColor(LABEL_TEXT)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(3));
            }

            if (Boolean.TRUE.equals(product.isPrescriptionRequired())) {
                document.add(new Paragraph("Prescription Required")
                        .setFontSize(7)
                        .setBold()
                        .setFontColor(new DeviceRgb(185, 28, 28))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(0));
            }

            document.close();

            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate barcode label", ex);
        }
    }

    public byte[] generateProductLabelSheet(List<Product> products) {
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("At least one barcode-ready product is required");
        }

        return generateSheet(products, "Selected medicines ready for print-and-paste barcode labeling.");
    }

    public byte[] generateRepeatedProductLabelSheet(Product product, int quantity, LabelSheetSize size) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Product is required to generate repeated barcode labels");
        }
        if (product.getBarcode() == null || product.getBarcode().isBlank()) {
            throw new IllegalArgumentException("Barcode is required before printing repeated labels");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Label quantity must be at least 1");
        }

        return generateRepeatedSheet(
                java.util.Collections.nCopies(quantity, product),
                quantity + " repeated label" + (quantity == 1 ? "" : "s") + " ready for print-and-paste barcode use.",
                size != null ? size : LabelSheetSize.SHEET_40);
    }

    private byte[] generateSheet(List<Product> products, String subtitle) {
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("At least one barcode-ready product is required");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(24, 20, 24, 20);

            document.add(new Paragraph(appConfig.getAppName() + " Barcode Sheet")
                    .setFontSize(14)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(LABEL_TEXT)
                    .setMarginBottom(4));

            document.add(new Paragraph(subtitle)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(LABEL_MUTED)
                    .setMarginBottom(14));

            Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1, 1 }));
            table.setWidth(UnitValue.createPercentValue(100));

            int count = 0;
            for (Product product : products) {
                if (product == null || product.getBarcode() == null || product.getBarcode().isBlank()) {
                    continue;
                }
                table.addCell(buildSheetLabelCell(product, pdf));
                count++;
            }

            while (count % 3 != 0) {
                table.addCell(new Cell().setBorder(Border.NO_BORDER));
                count++;
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate barcode sheet", ex);
        }
    }

    private byte[] generateRepeatedSheet(List<Product> products, String subtitle, LabelSheetSize size) {
        SheetLayout layout = resolveLayout(size);
        boolean compactSheet = switch (size) {
            case SHEET_40, SHEET_48, SHEET_65, SHEET_80 -> true;
            default -> false;
        };

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(compactSheet ? 12 : 24, compactSheet ? 10 : 20, compactSheet ? 12 : 24, compactSheet ? 10 : 20);

            if (!compactSheet) {
                document.add(new Paragraph(appConfig.getAppName() + " Barcode Sheet")
                        .setFontSize(14)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(LABEL_TEXT)
                        .setMarginBottom(4));

                document.add(new Paragraph(subtitle + " Sheet: " + size.getLabel())
                        .setFontSize(9)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(LABEL_MUTED)
                        .setMarginBottom(14));
            }

            float[] columns = new float[layout.columns];
            java.util.Arrays.fill(columns, 1f);
            Table table = new Table(UnitValue.createPercentArray(columns));
            table.setWidth(UnitValue.createPercentValue(100));

            int count = 0;
            for (Product product : products) {
                if (product == null || product.getBarcode() == null || product.getBarcode().isBlank()) {
                    continue;
                }
                table.addCell(buildSheetLabelCell(product, pdf, layout));
                count++;
            }

            while (count % layout.columns != 0) {
                table.addCell(new Cell().setBorder(Border.NO_BORDER));
                count++;
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate repeated barcode sheet", ex);
        }
    }

    private String buildMetaLine(Product product) {
        String manufacturer = product.getManufacturer() != null ? product.getManufacturer().trim() : "";
        String packSize = product.getPackSize() != null ? product.getPackSize().trim() : "";
        if (!manufacturer.isBlank() && !packSize.isBlank()) {
            return manufacturer + " • " + packSize;
        }
        return !manufacturer.isBlank() ? manufacturer : packSize;
    }

    private String buildPriceLine(Product product) {
        return buildPriceLine(product, false);
    }

    private String buildPriceLine(Product product, boolean compact) {
        StringBuilder builder = new StringBuilder();
        if (product.getPrice() != null) {
            builder.append(compact ? "SP: ₹" : "Sale ₹").append(product.getPrice().toPlainString());
        }
        if (product.getMrp() != null) {
            if (!builder.isEmpty()) {
                builder.append("   ");
            }
            builder.append(compact ? "MRP: ₹" : "MRP ₹").append(product.getMrp().toPlainString());
        }
        return builder.toString();
    }

    private Cell buildSheetLabelCell(Product product, PdfDocument pdf) {
        return buildSheetLabelCell(product, pdf, resolveLayout(LabelSheetSize.SHEET_24));
    }

    private Cell buildSheetLabelCell(Product product, PdfDocument pdf, SheetLayout layout) {
        Cell cell = new Cell()
                .setPadding(layout.padding)
                .setBorder(layout.showBorder ? new SolidBorder(LABEL_BORDER, 1) : Border.NO_BORDER)
                .setMinHeight(layout.minHeight)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        cell.add(new Paragraph(product.getName())
                .setFontSize(layout.titleFontSize)
                .setBold()
                .setFontColor(LABEL_TEXT)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargins(0, layout.horizontalTextMargin, 2, layout.horizontalTextMargin));

        String metaLine = buildMetaLine(product);
        if (layout.showMeta && !metaLine.isBlank()) {
            cell.add(new Paragraph(metaLine)
                    .setFontSize(layout.metaFontSize)
                    .setFontColor(LABEL_MUTED)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMargins(0, layout.horizontalTextMargin, layout.metaMarginBottom, layout.horizontalTextMargin));
        }

        Barcode128 barcode = new Barcode128(pdf);
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setCode(product.getBarcode().trim());
        barcode.setFont(null);
        barcode.setBarHeight(layout.barHeight);
        barcode.setX(layout.barWidth);

        Image barcodeImage = new Image(barcode.createFormXObject(LABEL_TEXT, LABEL_TEXT, pdf));
        barcodeImage.setAutoScale(true);
        barcodeImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
        barcodeImage.setMargins(0, layout.horizontalBarcodeMargin, layout.imageMarginBottom, layout.horizontalBarcodeMargin);
        cell.add(barcodeImage);

        cell.add(new Paragraph(product.getBarcode().trim())
                .setFontSize(layout.codeFontSize)
                .setBold()
                .setFontColor(LABEL_TEXT)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargins(0, layout.horizontalTextMargin, layout.codeMarginBottom, layout.horizontalTextMargin));

        String priceLine = buildPriceLine(product, layout.compactPriceText);
        if (layout.showPrice && !priceLine.isBlank()) {
            cell.add(new Paragraph(priceLine)
                    .setFontSize(layout.priceFontSize)
                    .setFontColor(LABEL_TEXT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMargins(0, layout.horizontalTextMargin, 0, layout.horizontalTextMargin));
        }

        return cell;
    }

    private SheetLayout resolveLayout(LabelSheetSize size) {
        return switch (size) {
            case SHEET_20 -> new SheetLayout(4, 150f, 12f, 10f, 0f, 8f, 7f, 0f, 5f, 26f, 0.92f, 4f, 0f, 8f, false, true, true, false);
            case SHEET_24 -> new SheetLayout(3, 126f, 10f, 9f, 0f, 7f, 6.5f, 0f, 4f, 22f, 0.82f, 3f, 0f, 7f, false, true, true, false);
            case SHEET_40 -> new SheetLayout(4, 80f, 3f, 5.6f, 0f, 4.8f, 4.5f, 0f, 1f, 18f, 0.56f, 0.5f, 4f, 5f, false, true, true, true);
            case SHEET_48 -> new SheetLayout(4, 68f, 2.5f, 5.1f, 0f, 4.4f, 4f, 0f, 1f, 16f, 0.5f, 0.4f, 3f, 4.5f, false, true, true, true);
            case SHEET_65 -> new SheetLayout(5, 54f, 2f, 4.4f, 0f, 3.9f, 3.5f, 0f, 0.8f, 13f, 0.4f, 0.3f, 2.2f, 3.5f, false, true, true, true);
            case SHEET_80 -> new SheetLayout(5, 44f, 1.8f, 3.8f, 0f, 3.4f, 3.1f, 0f, 0.7f, 11f, 0.34f, 0.2f, 1.6f, 3f, false, true, true, true);
        };
    }

    private record SheetLayout(
            int columns,
            float minHeight,
            float padding,
            float titleFontSize,
            float metaFontSize,
            float codeFontSize,
            float priceFontSize,
            float metaMarginBottom,
            float codeMarginBottom,
            float barHeight,
            float barWidth,
            float imageMarginBottom,
            float horizontalBarcodeMargin,
            float horizontalTextMargin,
            boolean showMeta,
            boolean showPrice,
            boolean showBorder,
            boolean compactPriceText) {
    }
}
