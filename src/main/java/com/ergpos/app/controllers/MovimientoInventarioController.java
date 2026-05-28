package com.ergpos.app.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.services.MovimientoInventarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/movimientos-inventario")
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;

    public MovimientoInventarioController(MovimientoInventarioService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @GetMapping
    public List<MovimientoInventario> getAll() {
        return movimientoService.findAll();
    }

    @GetMapping("/recientes")
    public List<MovimientoInventario> getRecientes() {
        return movimientoService.findRecientes();
    }

    // Registrar entrada de stock (compra a proveedor)
    @PostMapping("/entrada")
    public ResponseEntity<MovimientoInventario> registrarEntrada(@Valid @RequestBody MovimientoInventario movimiento) {
        try {
            return ResponseEntity.ok(movimientoService.registrarEntrada(movimiento));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Registrar salida de stock (uso interno, merma, etc.)
    @PostMapping("/salida")
    public ResponseEntity<MovimientoInventario> registrarSalida(@Valid @RequestBody MovimientoInventario movimiento) {
        try {
            return ResponseEntity.ok(movimientoService.registrarSalida(movimiento));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}