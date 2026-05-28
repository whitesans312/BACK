package com.ergpos.app.controllers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Producto;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.ProductoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final AuditoriaService auditoriaService;

    public ProductoController(ProductoService productoService,
                              AuditoriaService auditoriaService) {
        this.productoService = productoService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<Producto> getAllProductos() {
        return productoService.findAll();
    }

    @GetMapping("/activos")
    public List<Producto> getProductosActivos() {
        return productoService.findActiveProducts();
    }

    @GetMapping("/stock-bajo")
    public List<Producto> getProductosStockBajo() {
        return productoService.findProductosConStockBajo();
    }

    @GetMapping("/buscar")
    public List<Producto> buscar(@RequestParam String q) {
        return productoService.buscar(q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> getById(@PathVariable UUID id) {
        return productoService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<Producto> getByCodigo(@PathVariable String codigo) {
        return productoService.findByCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody Producto producto) {
        if (productoService.existsByCodigo(producto.getCodigo())) {
            return ResponseEntity.badRequest().body("Ya existe un producto con el código: " + producto.getCodigo());
        }
        try {
            Producto guardado = productoService.save(producto);
            auditoriaService.registrarActual(
                "CREAR_PRODUCTO", "INVENTARIO",
                guardado.getId(),
                "Producto creado: " + guardado.getNombre() + " (" + guardado.getCodigo() + ")"
            );
            return ResponseEntity.ok(guardado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al guardar el producto: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id, @Valid @RequestBody Producto productoDetails) {
        Optional<Producto> opt = productoService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Producto existing = opt.get();
        existing.setCodigo(productoDetails.getCodigo());
        existing.setNombre(productoDetails.getNombre());
        existing.setDescripcion(productoDetails.getDescripcion());
        existing.setCategoria(productoDetails.getCategoria());
        existing.setPrecio(productoDetails.getPrecio());
        existing.setStock(productoDetails.getStock());
        existing.setStockMinimo(productoDetails.getStockMinimo());

        try {
            Producto actualizado = productoService.save(existing);
            auditoriaService.registrarActual(
                "EDITAR_PRODUCTO", "INVENTARIO",
                actualizado.getId(),
                "Producto editado: " + actualizado.getNombre() + " (" + actualizado.getCodigo() + ")"
            );
            return ResponseEntity.ok(actualizado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al actualizar el producto: " + e.getMessage());
        }
    }

    // Soft delete
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        Optional<Producto> opt = productoService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Producto p = opt.get();
        p.setActivo(false);
        Producto actualizado = productoService.save(p);
        auditoriaService.registrarActual(
            "DESACTIVAR_PRODUCTO", "INVENTARIO",
            actualizado.getId(),
            "Producto desactivado: " + actualizado.getNombre()
        );
        return ResponseEntity.ok("Producto desactivado correctamente");
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<Producto> toggleActivo(@PathVariable UUID id, @RequestParam boolean activo) {
        Optional<Producto> opt = productoService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Producto p = opt.get();
        p.setActivo(activo);
        Producto actualizado = productoService.save(p);
        auditoriaService.registrarActual(
            activo ? "ACTIVAR_PRODUCTO" : "DESACTIVAR_PRODUCTO", "INVENTARIO",
            actualizado.getId(),
            (activo ? "Producto activado: " : "Producto desactivado: ") + actualizado.getNombre()
        );
        return ResponseEntity.ok(actualizado);
    }
}
