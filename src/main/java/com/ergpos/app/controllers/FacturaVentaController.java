// ── FacturaVentaController.java ───────────────────────────────────────────────
package com.ergpos.app.controllers;
 
import java.util.List;
import java.util.UUID;
 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.FacturaVenta;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.services.FacturaVentaService;
import com.ergpos.app.services.UsuarioService;
 
@RestController
@RequestMapping("/api/facturas-venta")
public class FacturaVentaController {
 
    private final FacturaVentaService facturaService;
    private final UsuarioService usuarioService;
 
    public FacturaVentaController(FacturaVentaService facturaService,
                                   UsuarioService usuarioService) {
        this.facturaService = facturaService;
        this.usuarioService = usuarioService;
    }
 
    @GetMapping
    public List<FacturaVenta> getAll() { return facturaService.findAll(); }
 
    @GetMapping("/{id}")
    public ResponseEntity<FacturaVenta> getById(@PathVariable UUID id) {
        return facturaService.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
 
    @GetMapping("/venta/{ventaId}")
    public ResponseEntity<FacturaVenta> getByVentaId(@PathVariable UUID ventaId) {
        return facturaService.findByVentaId(ventaId).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
 
    // Generar factura para una venta
    @PostMapping("/generar/{ventaId}")
    public ResponseEntity<Object> generar(@PathVariable UUID ventaId,
                                           @RequestParam UUID usuarioId) {
        try {
            Usuario usuario = usuarioService.findById(usuarioId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            return ResponseEntity.ok(facturaService.generarFactura(ventaId, usuario));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}