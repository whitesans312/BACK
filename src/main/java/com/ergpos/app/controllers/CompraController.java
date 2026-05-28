package com.ergpos.app.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Compra;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.services.CompraService;
import com.ergpos.app.services.UsuarioService;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;
    private final UsuarioService usuarioService;

    public CompraController(CompraService compraService,
                             UsuarioService usuarioService) {
        this.compraService  = compraService;
        this.usuarioService = usuarioService;
    }

    // GET /api/compras
    @GetMapping
    public List<Compra> getAll() { return compraService.findAll(); }

    // GET /api/compras/recientes
    @GetMapping("/recientes")
    public List<Compra> getRecientes() { return compraService.findRecientes(); }

    // GET /api/compras/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Compra> getById(@PathVariable UUID id) {
        return compraService.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/compras
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Compra compra) {
        try {
            return ResponseEntity.ok(compraService.save(compra));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PATCH /api/compras/{id}/confirmar?usuarioId=xxx
    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<Object> confirmar(@PathVariable UUID id,
                                             @RequestParam UUID usuarioId) {
        try {
            Usuario usuario = usuarioService.findById(usuarioId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            return ResponseEntity.ok(compraService.confirmar(id, usuario));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PATCH /api/compras/{id}/cancelar
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Object> cancelar(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(compraService.cancelar(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}