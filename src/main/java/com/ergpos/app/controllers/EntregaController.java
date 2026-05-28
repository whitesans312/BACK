package com.ergpos.app.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.ergpos.app.model.Entrega;
import com.ergpos.app.services.EntregaService;

@RestController
@RequestMapping("/api/entregas")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    // ── Consultas ───────────────────────────────────────────

    @GetMapping
    public List<Entrega> getAll() {
        return entregaService.findAll();
    }

    @GetMapping("/estado/{estado}")
    public List<Entrega> getByEstado(@PathVariable String estado) {
        return entregaService.findByEstado(estado);
    }

    @GetMapping("/tecnico/{tecnicoId}")
    public List<Entrega> getByTecnico(@PathVariable UUID tecnicoId) {
        return entregaService.findByTecnico(tecnicoId);
    }

    @GetMapping("/pago-pendiente")
    public List<Entrega> getPagoPendiente() {
        return entregaService.findConPagoPendiente();
    }

    @GetMapping("/por-confirmar")
    public List<Entrega> getPorConfirmar() {
        return entregaService.findPorConfirmar();
    }

    @GetMapping("/resumen")
    public Map<String, Long> getResumen() {
        return entregaService.contarPorEstado();
    }

    @GetMapping("/contadores")
    public Map<String, Long> getContadores() {
        return Map.of(
            "porConfirmar", entregaService.contarPorConfirmar(),
            "pagoPendiente", entregaService.contarPagoPendiente()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Entrega> getById(@PathVariable UUID id) {
        return entregaService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Crear ───────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Entrega entrega) {
        try {
            return ResponseEntity.status(201).body(entregaService.save(entrega));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Actualizar ──────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id, @RequestBody Entrega det) {
        return entregaService.findById(id).map(existing -> {

            existing.setTecnico(det.getTecnico());
            existing.setDireccion(det.getDireccion());
            existing.setClienteNombre(det.getClienteNombre());
            existing.setClienteTelefono(det.getClienteTelefono());
            existing.setCliente(det.getCliente());
            existing.setTipo(det.getTipo());
            existing.setDescripcionProblema(det.getDescripcionProblema());
            existing.setManoObra(det.getManoObra());

            try {
                return ResponseEntity.ok(
                        (Object) entregaService.update(existing, det.getItems()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) e.getMessage());
            }

        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Cambiar estado ─────────────────────────────────────

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Object> cambiarEstado(
            @PathVariable UUID id,
            @RequestParam String estado,
            @RequestParam(required = false) String notas) {

        if ("CANCELADO".equalsIgnoreCase(estado) && !isAdmin()) {
            return ResponseEntity.status(403).body("Solo un administrador puede cancelar órdenes");
        }

        try {
            return ResponseEntity.ok(
                    (Object) entregaService.cambiarEstado(id, estado, notas));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Confirmar servicio (FINALIZAR) ─────────────────────

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Object> confirmarServicio(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID adminId) {

        try {
            return ResponseEntity.ok(
                    entregaService.confirmarServicio(id, adminId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Pagos ──────────────────────────────────────────────

    @PostMapping("/{id}/pagos")
    public ResponseEntity<Object> registrarPago(
            @PathVariable UUID id,
            @RequestParam BigDecimal monto,
            @RequestParam(required = false) String notas,
            @RequestParam(required = false) UUID usuarioId) {

        try {
            return ResponseEntity.ok(
                    entregaService.registrarPago(id, monto, notas, usuarioId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Anticipo ───────────────────────────────────────────

    @PatchMapping("/{id}/anticipo")
    public ResponseEntity<Object> configurarAnticipo(
            @PathVariable UUID id,
            @RequestParam BigDecimal porcentaje) {

        try {
            return ResponseEntity.ok(
                    entregaService.configurarAnticipo(id, porcentaje));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Cancelar ───────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable UUID id) {

        if (entregaService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        entregaService.cambiarEstado(id, "CANCELADO", "Cancelado por administrador");
        return ResponseEntity.ok("Orden cancelada correctamente");
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
