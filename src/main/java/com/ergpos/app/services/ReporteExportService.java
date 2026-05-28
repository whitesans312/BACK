package com.ergpos.app.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.awt.Color;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import com.ergpos.app.model.Venta;
import com.ergpos.app.model.VentaItem;
import com.ergpos.app.model.Producto;
import com.ergpos.app.repositories.VentaRepository;
import com.ergpos.app.repositories.ProductoRepository;
import com.ergpos.app.repositories.CompraRepository;
import com.ergpos.app.repositories.EntregaRepository;
import com.ergpos.app.repositories.DevolucionGarantiaRepository;

@Service
public class ReporteExportService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final CompraRepository compraRepository;
    private final EntregaRepository entregaRepository;
    private final DevolucionGarantiaRepository devolucionGarantiaRepository;
    private final ConfiguracionNegocioService configService;

    public ReporteExportService(VentaRepository ventaRepository,
                                ProductoRepository productoRepository,
                                CompraRepository compraRepository,
                                EntregaRepository entregaRepository,
                                DevolucionGarantiaRepository devolucionGarantiaRepository,
                                ConfiguracionNegocioService configService) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.compraRepository = compraRepository;
        this.entregaRepository = entregaRepository;
        this.devolucionGarantiaRepository = devolucionGarantiaRepository;
        this.configService = configService;
    }

    public byte[] exportarVentasExcel(LocalDateTime desde, LocalDateTime hasta) {
        List<Venta> ventas = ventaRepository.findByFechaBetween(desde, hasta);
        BigDecimal taxDivisor = configService.getTaxDivisor();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            // Colores personalizados
            byte[] rgbSlate800 = new byte[]{(byte) 15, (byte) 23, (byte) 42}; // Navy #0f172a
            XSSFColor headerColor = new XSSFColor(rgbSlate800, null);

            // Estilos comunes
            XSSFFont headerFont = (XSSFFont) workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFillForegroundColor(headerColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);

            DataFormat df = workbook.createDataFormat();

            CellStyle borderStyle = workbook.createCellStyle();
            setBorders(borderStyle);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd hh:mm"));
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorders(dateStyle);

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(df.getFormat("$#,##0"));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorders(currencyStyle);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorders(centerStyle);

            CellStyle totalLabelStyle = workbook.createCellStyle();
            XSSFFont boldFont = (XSSFFont) workbook.createFont();
            boldFont.setBold(true);
            totalLabelStyle.setFont(boldFont);
            totalLabelStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalLabelStyle.setBorderTop(BorderStyle.DOUBLE);
            totalLabelStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle totalCurrencyStyle = workbook.createCellStyle();
            totalCurrencyStyle.setFont(boldFont);
            totalCurrencyStyle.setDataFormat(df.getFormat("$#,##0"));
            totalCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalCurrencyStyle.setBorderTop(BorderStyle.DOUBLE);
            totalCurrencyStyle.setBorderBottom(BorderStyle.THIN);

            // HOJA 1: RESUMEN DE VENTAS
            Sheet sheet1 = workbook.createSheet("Resumen Ventas");
            Row rowHeader1 = sheet1.createRow(0);
            rowHeader1.setHeightInPoints(24);
            
            String[] headers1 = {
                "ID Venta", "Fecha y Hora", "Cliente", "Teléfono", "Vendedor", "Estado", "Subtotal (Base)", "IVA", "Total"
            };
            for (int i = 0; i < headers1.length; i++) {
                Cell cell = rowHeader1.createCell(i);
                cell.setCellValue(headers1[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            BigDecimal sumSubtotal = BigDecimal.ZERO;
            BigDecimal sumIva = BigDecimal.ZERO;
            BigDecimal sumTotal = BigDecimal.ZERO;

            for (Venta v : ventas) {
                Row row = sheet1.createRow(rowIdx++);
                
                Cell cellId = row.createCell(0);
                cellId.setCellValue(v.getId().toString().substring(0, 8).toUpperCase());
                cellId.setCellStyle(centerStyle);

                Cell cellFecha = row.createCell(1);
                cellFecha.setCellValue(v.getFecha());
                cellFecha.setCellStyle(dateStyle);

                Cell cellCliente = row.createCell(2);
                cellCliente.setCellValue(v.getClienteNombre());
                cellCliente.setCellStyle(borderStyle);

                Cell cellTel = row.createCell(3);
                cellTel.setCellValue(v.getClienteTelefono() != null ? v.getClienteTelefono() : "—");
                cellTel.setCellStyle(centerStyle);

                Cell cellVend = row.createCell(4);
                cellVend.setCellValue(v.getVendedor() != null ? v.getVendedor().getNombre() : "—");
                cellVend.setCellStyle(borderStyle);

                Cell cellEst = row.createCell(5);
                cellEst.setCellValue(v.getEstado());
                cellEst.setCellStyle(centerStyle);

                BigDecimal total = v.getTotal();
                BigDecimal subtotal = total.divide(taxDivisor, 2, RoundingMode.HALF_UP);
                BigDecimal iva = total.subtract(subtotal);

                Cell cellSub = row.createCell(6);
                cellSub.setCellValue(subtotal.doubleValue());
                cellSub.setCellStyle(currencyStyle);

                Cell cellIva = row.createCell(7);
                cellIva.setCellValue(iva.doubleValue());
                cellIva.setCellStyle(currencyStyle);

                Cell cellTotal = row.createCell(8);
                cellTotal.setCellValue(total.doubleValue());
                cellTotal.setCellStyle(currencyStyle);

                // Solo sumar completadas
                if ("COMPLETADA".equals(v.getEstado())) {
                    sumSubtotal = sumSubtotal.add(subtotal);
                    sumIva = sumIva.add(iva);
                    sumTotal = sumTotal.add(total);
                }
            }

            // Fila de totales en Hoja 1
            Row rowTotal1 = sheet1.createRow(rowIdx);
            Cell cellLabel1 = rowTotal1.createCell(5);
            cellLabel1.setCellValue("TOTAL COMPLETADAS");
            cellLabel1.setCellStyle(totalLabelStyle);

            Cell cellTotalSub = rowTotal1.createCell(6);
            cellTotalSub.setCellValue(sumSubtotal.doubleValue());
            cellTotalSub.setCellStyle(totalCurrencyStyle);

            Cell cellTotalIva = rowTotal1.createCell(7);
            cellTotalIva.setCellValue(sumIva.doubleValue());
            cellTotalIva.setCellStyle(totalCurrencyStyle);

            Cell cellTotalTot = rowTotal1.createCell(8);
            cellTotalTot.setCellValue(sumTotal.doubleValue());
            cellTotalTot.setCellStyle(totalCurrencyStyle);

            // Rellenar bordes para celdas de total vacías
            for (int i = 0; i < 5; i++) {
                rowTotal1.createCell(i).setCellStyle(totalLabelStyle);
            }

            // HOJA 2: DETALLE DE ITEMS
            Sheet sheet2 = workbook.createSheet("Detalle Ítems");
            Row rowHeader2 = sheet2.createRow(0);
            rowHeader2.setHeightInPoints(24);

            String[] headers2 = {
                "ID Venta", "Fecha", "Código Producto", "Nombre Producto", "Categoría", "Cantidad", "Precio Unitario (c/IVA)", "Subtotal (c/IVA)", "Base Gravable (s/IVA)", "IVA"
            };
            for (int i = 0; i < headers2.length; i++) {
                Cell cell = rowHeader2.createCell(i);
                cell.setCellValue(headers2[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx2 = 1;
            int totalQty = 0;
            BigDecimal sumSubtotal2 = BigDecimal.ZERO;
            BigDecimal sumTotal2 = BigDecimal.ZERO;
            BigDecimal sumIva2 = BigDecimal.ZERO;

            for (Venta v : ventas) {
                if (!"COMPLETADA".equals(v.getEstado())) continue; // Solo exportar ítems de ventas completadas

                for (VentaItem item : v.getItems()) {
                    Row row = sheet2.createRow(rowIdx2++);

                    Cell cellId = row.createCell(0);
                    cellId.setCellValue(v.getId().toString().substring(0, 8).toUpperCase());
                    cellId.setCellStyle(centerStyle);

                    Cell cellFecha = row.createCell(1);
                    cellFecha.setCellValue(v.getFecha());
                    cellFecha.setCellStyle(dateStyle);

                    Cell cellCod = row.createCell(2);
                    cellCod.setCellValue(item.getProducto() != null ? item.getProducto().getCodigo() : "—");
                    cellCod.setCellStyle(centerStyle);

                    Cell cellNom = row.createCell(3);
                    cellNom.setCellValue(item.getProducto() != null ? item.getProducto().getNombre() : "—");
                    cellNom.setCellStyle(borderStyle);

                    Cell cellCat = row.createCell(4);
                    cellCat.setCellValue(item.getProducto() != null && item.getProducto().getCategoria() != null ? item.getProducto().getCategoria().getNombre() : "—");
                    cellCat.setCellStyle(borderStyle);

                    Cell cellCant = row.createCell(5);
                    cellCant.setCellValue(item.getCantidad());
                    cellCant.setCellStyle(centerStyle);

                    Cell cellPrec = row.createCell(6);
                    cellPrec.setCellValue(item.getPrecioUnitario().doubleValue());
                    cellPrec.setCellStyle(currencyStyle);

                    BigDecimal totalItem = item.getSubtotal();
                    BigDecimal subtotalItem = totalItem.divide(taxDivisor, 2, RoundingMode.HALF_UP);
                    BigDecimal ivaItem = totalItem.subtract(subtotalItem);

                    Cell cellSubt = row.createCell(7);
                    cellSubt.setCellValue(totalItem.doubleValue());
                    cellSubt.setCellStyle(currencyStyle);

                    Cell cellBase = row.createCell(8);
                    cellBase.setCellValue(subtotalItem.doubleValue());
                    cellBase.setCellStyle(currencyStyle);

                    Cell cellIvaIt = row.createCell(9);
                    cellIvaIt.setCellValue(ivaItem.doubleValue());
                    cellIvaIt.setCellStyle(currencyStyle);

                    totalQty += item.getCantidad();
                    sumTotal2 = sumTotal2.add(totalItem);
                    sumSubtotal2 = sumSubtotal2.add(subtotalItem);
                    sumIva2 = sumIva2.add(ivaItem);
                }
            }

            // Fila de totales en Hoja 2
            Row rowTotal2 = sheet2.createRow(rowIdx2);
            Cell cellLabel2 = rowTotal2.createCell(4);
            cellLabel2.setCellValue("TOTALES");
            cellLabel2.setCellStyle(totalLabelStyle);

            Cell cellTotalQty = rowTotal2.createCell(5);
            cellTotalQty.setCellValue(totalQty);
            cellTotalQty.setCellStyle(centerStyle); // simple format
            
            // apply double line border to qty
            CellStyle totalQtyStyle = workbook.createCellStyle();
            totalQtyStyle.setFont(boldFont);
            totalQtyStyle.setAlignment(HorizontalAlignment.CENTER);
            totalQtyStyle.setBorderTop(BorderStyle.DOUBLE);
            totalQtyStyle.setBorderBottom(BorderStyle.THIN);
            cellTotalQty.setCellStyle(totalQtyStyle);

            Cell cellTotalSubt2 = rowTotal2.createCell(7);
            cellTotalSubt2.setCellValue(sumTotal2.doubleValue());
            cellTotalSubt2.setCellStyle(totalCurrencyStyle);

            Cell cellTotalBase2 = rowTotal2.createCell(8);
            cellTotalBase2.setCellValue(sumSubtotal2.doubleValue());
            cellTotalBase2.setCellStyle(totalCurrencyStyle);

            Cell cellTotalIva2 = rowTotal2.createCell(9);
            cellTotalIva2.setCellValue(sumIva2.doubleValue());
            cellTotalIva2.setCellStyle(totalCurrencyStyle);

            for (int i = 0; i < 4; i++) {
                rowTotal2.createCell(i).setCellStyle(totalLabelStyle);
            }
            rowTotal2.createCell(6).setCellStyle(totalLabelStyle);

            // Ajustar anchos
            autoSizeOrSetWidths(sheet1, headers1.length, new int[]{15, 20, 25, 15, 20, 15, 15, 15, 15});
            autoSizeOrSetWidths(sheet2, headers2.length, new int[]{15, 20, 18, 30, 20, 12, 20, 20, 20, 15});

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de ventas", e);
        }
    }

    public byte[] exportarVentasPdf(LocalDateTime desde, LocalDateTime hasta) {
        List<Venta> ventas = ventaRepository.findByFechaBetween(desde, hasta);
        BigDecimal taxDivisor = configService.getTaxDivisor();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER.rotate()); // Horizontal
            PdfWriter.getInstance(document, out);
            document.open();

            // Metadata
            document.addTitle("Reporte de Ventas - ERGPOS");

            // Título
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(15, 23, 42));
            Paragraph title = new Paragraph("REPORTE DE VENTAS", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            // Rango de fechas
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
            Paragraph dateRange = new Paragraph(
                "Periodo: " + desde.format(formatter) + "  a  " + hasta.format(formatter),
                dateFont
            );
            dateRange.setAlignment(Element.ALIGN_CENTER);
            dateRange.setSpacingAfter(20);
            document.add(dateRange);

            // Tabla
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.2f, 2.3f, 3.2f, 2.3f, 2.0f, 1.8f, 1.8f, 2.0f});

            // Cabeceras
            String[] headers = {"ID", "Fecha", "Cliente", "Vendedor", "Estado", "Subtotal", "IVA", "Total"};
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Color headerBg = new Color(15, 23, 42); // Slate 900

            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
                cell.setBackgroundColor(headerBg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(8);
                table.addCell(cell);
            }

            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            BigDecimal totalSubtotal = BigDecimal.ZERO;
            BigDecimal totalIva = BigDecimal.ZERO;
            BigDecimal totalMonto = BigDecimal.ZERO;

            for (Venta v : ventas) {
                BigDecimal total = v.getTotal();
                BigDecimal subtotal = total.divide(taxDivisor, 2, RoundingMode.HALF_UP);
                BigDecimal iva = total.subtract(subtotal);

                if ("COMPLETADA".equals(v.getEstado())) {
                    totalSubtotal = totalSubtotal.add(subtotal);
                    totalIva = totalIva.add(iva);
                    totalMonto = totalMonto.add(total);
                }

                // ID
                PdfPCell cellId = new PdfPCell(new Phrase(v.getId().toString().substring(0, 8).toUpperCase(), dataFont));
                cellId.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellId.setPadding(6);
                table.addCell(cellId);

                // Fecha
                PdfPCell cellFecha = new PdfPCell(new Phrase(v.getFecha().format(formatter), dataFont));
                cellFecha.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellFecha.setPadding(6);
                table.addCell(cellFecha);

                // Cliente
                PdfPCell cellCliente = new PdfPCell(new Phrase(v.getClienteNombre(), dataFont));
                cellCliente.setPadding(6);
                table.addCell(cellCliente);

                // Vendedor
                String vend = v.getVendedor() != null ? v.getVendedor().getNombre() : "—";
                PdfPCell cellVend = new PdfPCell(new Phrase(vend, dataFont));
                cellVend.setPadding(6);
                table.addCell(cellVend);

                // Estado
                PdfPCell cellEst = new PdfPCell(new Phrase(v.getEstado(), dataFont));
                cellEst.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellEst.setPadding(6);
                if ("COMPLETADA".equals(v.getEstado())) {
                    cellEst.setBackgroundColor(new Color(220, 252, 231)); // verde claro
                } else if ("CANCELADA".equals(v.getEstado())) {
                    cellEst.setBackgroundColor(new Color(254, 226, 226)); // rojo claro
                } else {
                    cellEst.setBackgroundColor(new Color(254, 243, 199)); // amarillo claro
                }
                table.addCell(cellEst);

                // Subtotal
                PdfPCell cellSub = new PdfPCell(new Phrase("$" + String.format("%,.0f", subtotal), dataFont));
                cellSub.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellSub.setPadding(6);
                table.addCell(cellSub);

                // IVA
                PdfPCell cellIva = new PdfPCell(new Phrase("$" + String.format("%,.0f", iva), dataFont));
                cellIva.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellIva.setPadding(6);
                table.addCell(cellIva);

                // Total
                PdfPCell cellTot = new PdfPCell(new Phrase("$" + String.format("%,.0f", total), dataFont));
                cellTot.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellTot.setPadding(6);
                table.addCell(cellTot);
            }

            // Fila de totales
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Color totalBg = new Color(241, 245, 249); // Slate 100

            PdfPCell cellTotalLabel = new PdfPCell(new Phrase("TOTAL COMPLETADAS", boldFont));
            cellTotalLabel.setColspan(5);
            cellTotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellTotalLabel.setPadding(8);
            cellTotalLabel.setBackgroundColor(totalBg);
            table.addCell(cellTotalLabel);

            PdfPCell cellTotalSub = new PdfPCell(new Phrase("$" + String.format("%,.0f", totalSubtotal), boldFont));
            cellTotalSub.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellTotalSub.setPadding(8);
            cellTotalSub.setBackgroundColor(totalBg);
            table.addCell(cellTotalSub);

            PdfPCell cellTotalIva = new PdfPCell(new Phrase("$" + String.format("%,.0f", totalIva), boldFont));
            cellTotalIva.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellTotalIva.setPadding(8);
            cellTotalIva.setBackgroundColor(totalBg);
            table.addCell(cellTotalIva);

            PdfPCell cellTotalTot = new PdfPCell(new Phrase("$" + String.format("%,.0f", totalMonto), boldFont));
            cellTotalTot.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellTotalTot.setPadding(8);
            cellTotalTot.setBackgroundColor(totalBg);
            table.addCell(cellTotalTot);

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF de ventas", e);
        }
    }

    public byte[] exportarInventarioExcel() {
        List<Producto> productos = productoRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Inventario");

            // Estilos
            byte[] rgbSlate800 = new byte[]{(byte) 15, (byte) 23, (byte) 42};
            XSSFColor headerBg = new XSSFColor(rgbSlate800, null);

            XSSFFont headerFont = (XSSFFont) workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFillForegroundColor(headerBg);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);

            DataFormat df = workbook.createDataFormat();

            CellStyle borderStyle = workbook.createCellStyle();
            setBorders(borderStyle);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorders(centerStyle);

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(df.getFormat("$#,##0"));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorders(currencyStyle);

            CellStyle totalLabelStyle = workbook.createCellStyle();
            XSSFFont boldFont = (XSSFFont) workbook.createFont();
            boldFont.setBold(true);
            totalLabelStyle.setFont(boldFont);
            totalLabelStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalLabelStyle.setBorderTop(BorderStyle.DOUBLE);
            totalLabelStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle totalCurrencyStyle = workbook.createCellStyle();
            totalCurrencyStyle.setFont(boldFont);
            totalCurrencyStyle.setDataFormat(df.getFormat("$#,##0"));
            totalCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalCurrencyStyle.setBorderTop(BorderStyle.DOUBLE);
            totalCurrencyStyle.setBorderBottom(BorderStyle.THIN);

            // Cabeceras
            Row rowHeader = sheet.createRow(0);
            rowHeader.setHeightInPoints(24);
            String[] headers = {
                "Código", "Nombre", "Descripción", "Categoría", "Stock Actual", "Stock Mínimo", "Precio Venta (c/IVA)", "Valor de Inventario", "Estado Stock", "Activo"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = rowHeader.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            int totalStock = 0;
            BigDecimal totalInventarioValor = BigDecimal.ZERO;

            for (Producto p : productos) {
                Row row = sheet.createRow(rowIdx++);

                Cell cellCod = row.createCell(0);
                cellCod.setCellValue(p.getCodigo());
                cellCod.setCellStyle(centerStyle);

                Cell cellNom = row.createCell(1);
                cellNom.setCellValue(p.getNombre());
                cellNom.setCellStyle(borderStyle);

                Cell cellDesc = row.createCell(2);
                cellDesc.setCellValue(p.getDescripcion() != null ? p.getDescripcion() : "—");
                cellDesc.setCellStyle(borderStyle);

                Cell cellCat = row.createCell(3);
                cellCat.setCellValue(p.getCategoria() != null ? p.getCategoria().getNombre() : "—");
                cellCat.setCellStyle(borderStyle);

                Cell cellStock = row.createCell(4);
                cellStock.setCellValue(p.getStock());
                cellStock.setCellStyle(centerStyle);

                Cell cellMin = row.createCell(5);
                cellMin.setCellValue(p.getStockMinimo() != null ? p.getStockMinimo() : 0);
                cellMin.setCellStyle(centerStyle);

                Cell cellPrec = row.createCell(6);
                cellPrec.setCellValue(p.getPrecio().doubleValue());
                cellPrec.setCellStyle(currencyStyle);

                BigDecimal valorInv = p.getValorInventario();
                Cell cellVal = row.createCell(7);
                cellVal.setCellValue(valorInv.doubleValue());
                cellVal.setCellStyle(currencyStyle);

                // Estado de Stock
                String estStock = "OK";
                if (p.getStock() <= 0) {
                    estStock = "AGOTADO";
                } else if (p.getStockMinimo() != null && p.getStock() <= p.getStockMinimo()) {
                    estStock = "STOCK BAJO";
                }
                Cell cellEst = row.createCell(8);
                cellEst.setCellValue(estStock);
                cellEst.setCellStyle(centerStyle);

                Cell cellAct = row.createCell(9);
                cellAct.setCellValue(Boolean.TRUE.equals(p.getActivo()) ? "SÍ" : "NO");
                cellAct.setCellStyle(centerStyle);

                if (Boolean.TRUE.equals(p.getActivo())) {
                    totalStock += p.getStock();
                    totalInventarioValor = totalInventarioValor.add(valorInv);
                }
            }

            // Fila de totales
            Row rowTotal = sheet.createRow(rowIdx);
            Cell cellLabel = rowTotal.createCell(3);
            cellLabel.setCellValue("TOTAL ACTIVOS");
            cellLabel.setCellStyle(totalLabelStyle);

            Cell cellTotalStock = rowTotal.createCell(4);
            cellTotalStock.setCellValue(totalStock);
            CellStyle totalStockStyle = workbook.createCellStyle();
            totalStockStyle.setFont(boldFont);
            totalStockStyle.setAlignment(HorizontalAlignment.CENTER);
            totalStockStyle.setBorderTop(BorderStyle.DOUBLE);
            totalStockStyle.setBorderBottom(BorderStyle.THIN);
            cellTotalStock.setCellStyle(totalStockStyle);

            Cell cellTotalVal = rowTotal.createCell(7);
            cellTotalVal.setCellValue(totalInventarioValor.doubleValue());
            cellTotalVal.setCellStyle(totalCurrencyStyle);

            for (int i = 0; i < 3; i++) {
                rowTotal.createCell(i).setCellStyle(totalLabelStyle);
            }
            rowTotal.createCell(5).setCellStyle(totalLabelStyle);
            rowTotal.createCell(6).setCellStyle(totalLabelStyle);
            rowTotal.createCell(8).setCellStyle(totalLabelStyle);
            rowTotal.createCell(9).setCellStyle(totalLabelStyle);

            autoSizeOrSetWidths(sheet, headers.length, new int[]{15, 25, 30, 20, 12, 12, 20, 20, 15, 10});

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar Excel de inventario", e);
        }
    }

    public byte[] exportarFinanzasExcel(LocalDateTime desde, LocalDateTime hasta) {
        Double totalVentasPOS = ventaRepository.sumTotalCompletadasEnRango(desde, hasta);
        Double totalServicios = entregaRepository.sumTotalFinalizadasEnRango(desde, hasta);
        Double totalCompras = compraRepository.sumTotalConfirmadasEnRango(desde, hasta);
        Double totalDevoluciones = devolucionGarantiaRepository.sumTotalDevueltoEnRango(desde, hasta);

        double ventasVal = totalVentasPOS != null ? totalVentasPOS : 0.0;
        double serviciosVal = totalServicios != null ? totalServicios : 0.0;
        double comprasVal = totalCompras != null ? totalCompras : 0.0;
        double devolucionesVal = totalDevoluciones != null ? totalDevoluciones : 0.0;

        double ingresosBrutos = ventasVal + serviciosVal;
        double egresosVal = comprasVal + devolucionesVal;
        double utilidadNeta = ingresosBrutos - egresosVal;

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("P&L");

            // Estilos
            byte[] rgbNavy = new byte[]{(byte) 15, (byte) 23, (byte) 42};
            XSSFColor headerBg = new XSSFColor(rgbNavy, null);

            XSSFFont titleFont = (XSSFFont) workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFCellStyle titleStyle = (XSSFCellStyle) workbook.createCellStyle();
            titleStyle.setFillForegroundColor(headerBg);
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont sectionFont = (XSSFFont) workbook.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 11);
            sectionFont.setColor(new XSSFColor(new byte[]{(byte) 71, (byte) 85, (byte) 105}, null)); // Slate 600

            CellStyle sectionStyle = workbook.createCellStyle();
            sectionStyle.setFont(sectionFont);
            sectionStyle.setBorderBottom(BorderStyle.THIN);

            DataFormat df = workbook.createDataFormat();

            CellStyle labelStyle = workbook.createCellStyle();
            labelStyle.setBorderBottom(BorderStyle.DOTTED);

            CellStyle valueStyle = workbook.createCellStyle();
            valueStyle.setDataFormat(df.getFormat("$#,##0"));
            valueStyle.setAlignment(HorizontalAlignment.RIGHT);
            valueStyle.setBorderBottom(BorderStyle.DOTTED);

            XSSFFont boldFont = (XSSFFont) workbook.createFont();
            boldFont.setBold(true);

            CellStyle sumLabelStyle = workbook.createCellStyle();
            sumLabelStyle.setFont(boldFont);
            sumLabelStyle.setBorderBottom(BorderStyle.THIN);
            sumLabelStyle.setBorderTop(BorderStyle.THIN);

            CellStyle sumValueStyle = workbook.createCellStyle();
            sumValueStyle.setFont(boldFont);
            sumValueStyle.setDataFormat(df.getFormat("$#,##0"));
            sumValueStyle.setAlignment(HorizontalAlignment.RIGHT);
            sumValueStyle.setBorderBottom(BorderStyle.THIN);
            sumValueStyle.setBorderTop(BorderStyle.THIN);

            // Utilidad Neta Especial
            CellStyle netIncomeStyle = workbook.createCellStyle();
            XSSFFont largeBoldFont = (XSSFFont) workbook.createFont();
            largeBoldFont.setBold(true);
            largeBoldFont.setFontHeightInPoints((short) 12);
            netIncomeStyle.setFont(largeBoldFont);
            netIncomeStyle.setDataFormat(df.getFormat("$#,##0"));
            netIncomeStyle.setAlignment(HorizontalAlignment.RIGHT);
            netIncomeStyle.setBorderBottom(BorderStyle.DOUBLE);
            netIncomeStyle.setBorderTop(BorderStyle.THIN);

            if (utilidadNeta >= 0) {
                // light green fill
                netIncomeStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 220, (byte) 252, (byte) 231}, null));
            } else {
                // light red fill
                netIncomeStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 254, (byte) 226, (byte) 226}, null));
            }
            netIncomeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle netIncomeLabelStyle = workbook.createCellStyle();
            netIncomeLabelStyle.setFont(largeBoldFont);
            netIncomeLabelStyle.setBorderBottom(BorderStyle.DOUBLE);
            netIncomeLabelStyle.setBorderTop(BorderStyle.THIN);
            if (utilidadNeta >= 0) {
                netIncomeLabelStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 220, (byte) 252, (byte) 231}, null));
            } else {
                netIncomeLabelStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 254, (byte) 226, (byte) 226}, null));
            }
            netIncomeLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 1. TÍTULO
            Row rTitle = sheet.createRow(0);
            rTitle.setHeightInPoints(35);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("ESTADO DE RESULTADOS (P&L)");
            cTitle.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 2));

            // range dates
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            Row rPeriod = sheet.createRow(1);
            Cell cPeriod = rPeriod.createCell(0);
            cPeriod.setCellValue("Periodo: " + desde.format(formatter) + " a " + hasta.format(formatter));
            CellStyle pStyle = workbook.createCellStyle();
            XSSFFont pFont = (XSSFFont) workbook.createFont();
            pFont.setItalic(true);
            pFont.setColor(new XSSFColor(new byte[]{(byte) 100, (byte) 116, (byte) 139}, null));
            pStyle.setFont(pFont);
            cPeriod.setCellStyle(pStyle);

            // 2. SECCIÓN INGRESOS
            int curRow = 3;
            Row rSecInc = sheet.createRow(curRow++);
            Cell cSecInc = rSecInc.createCell(0);
            cSecInc.setCellValue("INGRESOS OPERACIONALES");
            cSecInc.setCellStyle(sectionStyle);
            rSecInc.createCell(1).setCellStyle(sectionStyle);
            rSecInc.createCell(2).setCellStyle(sectionStyle);

            Row rPOS = sheet.createRow(curRow++);
            Cell cPOSL = rPOS.createCell(0);
            cPOSL.setCellValue("Ventas POS (Productos)");
            cPOSL.setCellStyle(labelStyle);
            Cell cPOSV = rPOS.createCell(2);
            cPOSV.setCellValue(ventasVal);
            cPOSV.setCellStyle(valueStyle);

            Row rServ = sheet.createRow(curRow++);
            Cell cServL = rServ.createCell(0);
            cServL.setCellValue("Mano de Obra (Servicios)");
            cServL.setCellStyle(labelStyle);
            Cell cServV = rServ.createCell(2);
            cServV.setCellValue(serviciosVal);
            cServV.setCellStyle(valueStyle);

            Row rTotalInc = sheet.createRow(curRow++);
            Cell cTotIncL = rTotalInc.createCell(0);
            cTotIncL.setCellValue("TOTAL INGRESOS BRUTOS");
            cTotIncL.setCellStyle(sumLabelStyle);
            rTotalInc.createCell(1).setCellStyle(sumLabelStyle);
            Cell cTotIncV = rTotalInc.createCell(2);
            cTotIncV.setCellValue(ingresosBrutos);
            cTotIncV.setCellStyle(sumValueStyle);

            // 3. SECCIÓN EGRESOS
            curRow++;
            Row rSecExp = sheet.createRow(curRow++);
            Cell cSecExp = rSecExp.createCell(0);
            cSecExp.setCellValue("COSTOS Y DEDUCCIONES");
            cSecExp.setCellStyle(sectionStyle);
            rSecExp.createCell(1).setCellStyle(sectionStyle);
            rSecExp.createCell(2).setCellStyle(sectionStyle);

            Row rComp = sheet.createRow(curRow++);
            Cell cCompL = rComp.createCell(0);
            cCompL.setCellValue("Compras de Mercancía (Proveedores)");
            cCompL.setCellStyle(labelStyle);
            Cell cCompV = rComp.createCell(2);
            cCompV.setCellValue(comprasVal);
            cCompV.setCellStyle(valueStyle);

            Row rDev = sheet.createRow(curRow++);
            Cell cDevL = rDev.createCell(0);
            cDevL.setCellValue("Devoluciones y Garantías");
            cDevL.setCellStyle(labelStyle);
            Cell cDevV = rDev.createCell(2);
            cDevV.setCellValue(devolucionesVal);
            cDevV.setCellStyle(valueStyle);

            Row rTotalExp = sheet.createRow(curRow++);
            Cell cTotExpL = rTotalExp.createCell(0);
            cTotExpL.setCellValue("TOTAL COSTOS Y DEDUCCIONES");
            cTotExpL.setCellStyle(sumLabelStyle);
            rTotalExp.createCell(1).setCellStyle(sumLabelStyle);
            Cell cTotExpV = rTotalExp.createCell(2);
            cTotExpV.setCellValue(egresosVal);
            cTotExpV.setCellStyle(sumValueStyle);

            // 4. UTILIDAD NETA
            curRow += 2;
            Row rNet = sheet.createRow(curRow++);
            Cell cNetL = rNet.createCell(0);
            cNetL.setCellValue("UTILIDAD NETA DEL PERIODO");
            cNetL.setCellStyle(netIncomeLabelStyle);
            rNet.createCell(1).setCellStyle(netIncomeLabelStyle);
            Cell cNetV = rNet.createCell(2);
            cNetV.setCellValue(utilidadNeta);
            cNetV.setCellStyle(netIncomeStyle);

            sheet.setColumnWidth(0, 35 * 256);
            sheet.setColumnWidth(1, 5 * 256);
            sheet.setColumnWidth(2, 20 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar Excel de finanzas (P&L)", e);
        }
    }

    // Métodos auxiliares
    private void setBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderTop(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderLeft(BorderStyle.THIN);
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderRight(BorderStyle.THIN);
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private void autoSizeOrSetWidths(Sheet sheet, int colCount, int[] defaultWidths) {
        for (int i = 0; i < colCount; i++) {
            try {
                sheet.autoSizeColumn(i);
                // Si la columna es muy angosta, usar el default
                if (sheet.getColumnWidth(i) < defaultWidths[i] * 256) {
                    sheet.setColumnWidth(i, defaultWidths[i] * 256);
                }
            } catch (Exception e) {
                // Fallback a los defaults configurados
                sheet.setColumnWidth(i, defaultWidths[i] * 256);
            }
        }
    }
}
