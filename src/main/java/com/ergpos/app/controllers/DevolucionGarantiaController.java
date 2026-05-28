package com.ergpos.app.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.DevolucionGarantia;
import com.ergpos.app.services.DevolucionGarantiaService;

@RestController
@RequestMapping("/api/devoluciones-garantias")
public class DevolucionGarantiaController {

    private final DevolucionGarantiaService service;

    public DevolucionGarantiaController(DevolucionGarantiaService service) {
        this.service = service;
    }

    @GetMapping
    public List<DevolucionGarantia> getAll() {
        return service.findAll();
    }

    @GetMapping("/recientes")
    public List<DevolucionGarantia> getRecientes() {
        return service.findRecientes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DevolucionGarantia> getById(@PathVariable UUID id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/venta/{ventaId}")
    public List<DevolucionGarantia> getByVenta(@PathVariable UUID ventaId) {
        return service.findByVenta(ventaId);
    }

    @GetMapping("/entrega/{entregaId}")
    public List<DevolucionGarantia> getByEntrega(@PathVariable UUID entregaId) {
        return service.findByEntrega(entregaId);
    }

    @GetMapping("/venta/{ventaId}/tiene-activa")
    public ResponseEntity<Boolean> tieneActivaPorVenta(@PathVariable UUID ventaId) {
        return ResponseEntity.ok(service.tieneDevolucionActivaPorVenta(ventaId));
    }

    @GetMapping("/entrega/{entregaId}/tiene-activa")
    public ResponseEntity<Boolean> tieneActivaPorEntrega(@PathVariable UUID entregaId) {
        return ResponseEntity.ok(service.tieneDevolucionActivaPorEntrega(entregaId));
    }

    @PostMapping
    public ResponseEntity<Object> registrar(@RequestBody DevolucionGarantia solicitud) {
        try {
            return ResponseEntity.status(201).body(service.registrar(solicitud));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> anular(
            @PathVariable UUID id,
            @RequestParam(required = false) String motivo) {
        try {
            return ResponseEntity.ok(service.anular(id, motivo));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
