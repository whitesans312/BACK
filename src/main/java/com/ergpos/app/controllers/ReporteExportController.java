package com.ergpos.app.controllers;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.services.ReporteExportService;

@RestController
@RequestMapping("/api/reportes/exportar")
public class ReporteExportController {

    private final ReporteExportService exportService;

    public ReporteExportController(ReporteExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/ventas/excel")
    public ResponseEntity<byte[]> ventasExcel(
            @RequestParam("desde") String desdeStr,
            @RequestParam("hasta") String hastaStr) {
        
        LocalDateTime desde = LocalDate.parse(desdeStr).atStartOfDay();
        LocalDateTime hasta = LocalDate.parse(hastaStr).atTime(23, 59, 59);

        byte[] data = exportService.exportarVentasExcel(desde, hasta);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-ventas-" + desdeStr + "-a-" + hastaStr + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/ventas/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam("desde") String desdeStr,
            @RequestParam("hasta") String hastaStr) {
        
        LocalDateTime desde = LocalDate.parse(desdeStr).atStartOfDay();
        LocalDateTime hasta = LocalDate.parse(hastaStr).atTime(23, 59, 59);

        byte[] data = exportService.exportarVentasPdf(desde, hasta);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-ventas-" + desdeStr + "-a-" + hastaStr + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    @GetMapping("/inventario/excel")
    public ResponseEntity<byte[]> inventarioExcel() {
        byte[] data = exportService.exportarInventarioExcel();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-inventario.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/finanzas/excel")
    public ResponseEntity<byte[]> finanzasExcel(
            @RequestParam("desde") String desdeStr,
            @RequestParam("hasta") String hastaStr) {
        
        LocalDateTime desde = LocalDate.parse(desdeStr).atStartOfDay();
        LocalDateTime hasta = LocalDate.parse(hastaStr).atTime(23, 59, 59);

        byte[] data = exportService.exportarFinanzasExcel(desde, hasta);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-finanzas-" + desdeStr + "-a-" + hastaStr + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
