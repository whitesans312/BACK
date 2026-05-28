package com.ergpos.app.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ergpos.app.model.Venta;
import com.ergpos.app.services.VentaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    // ── Consultas ───────────────────────────────────────────

    @GetMapping
    public List<Venta> getAll() {
        return ventaService.findAll();
    }

    @GetMapping("/recientes")
    public List<Venta> getRecientes() {
        return ventaService.findRecientes();
    }

    @GetMapping("/resumen")
    public Map<String, Object> getResumen() {
        return ventaService.getResumenGeneral();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venta> getById(@PathVariable UUID id) {
        return ventaService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Crear venta ─────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody Venta venta) {
        try {
            return ResponseEntity.status(201).body(ventaService.save(venta));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Cancelar venta ─────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> cancelar(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(ventaService.cancelar(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}