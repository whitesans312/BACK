package com.ergpos.app.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Auditoria;
import com.ergpos.app.services.AuditoriaService;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * GET /api/auditoria
     * Retorna los últimos N registros (default 100).
     * Soporta filtros combinados: modulo, usuarioId y accion pueden usarse juntos.
     */
    @GetMapping
    public ResponseEntity<List<Auditoria>> getAuditoria(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidadTipo,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String severidad,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta) {

        LocalDate parsedDesde = null;
        LocalDate parsedHasta = null;

        try {
            if (fechaDesde != null && !fechaDesde.isBlank()) {
                parsedDesde = LocalDate.parse(fechaDesde);
            }
            if (fechaHasta != null && !fechaHasta.isBlank()) {
                parsedHasta = LocalDate.parse(fechaHasta);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        List<Auditoria> result = auditoriaService.findConFiltros(
                modulo, usuarioId, accion, entidadTipo, resultado, severidad,
                parsedDesde, parsedHasta, limit);
        return ResponseEntity.ok(result);
    }
}
