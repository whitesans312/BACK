package com.ergpos.app.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ergpos.app.model.Producto;
import com.ergpos.app.repositories.ProductoRepository;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    // ── Consultas ───────────────────────────────────────────

    public List<Producto> findAll() {
        return productoRepository.findAll();
    }

    public List<Producto> findActiveProducts() {
        return productoRepository.findByActivoTrue();
    }

    public List<Producto> findProductosConStockBajo() {
        return productoRepository.findProductosConStockBajo();
    }

    public List<Producto> buscar(String query) {
        return productoRepository.buscarPorNombreOCodigo(query);
    }

    public Optional<Producto> findById(UUID id) {
        return productoRepository.findById(id);
    }

    public Optional<Producto> findByCodigo(String codigo) {
        return productoRepository.findByCodigo(codigo);
    }

    public boolean existsByCodigo(String codigo) {
        return productoRepository.existsByCodigo(codigo);
    }

    // ── Guardar producto ────────────────────────────────────

    public Producto save(Producto producto) {

        // Validar código duplicado en creación
        if (producto.getId() == null &&
            productoRepository.existsByCodigo(producto.getCodigo())) {
            throw new IllegalArgumentException("Ya existe un producto con ese código");
        }

        // Validación básica extra
        if (producto.getNombre() == null || producto.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }

        return productoRepository.save(producto);
    }

    // ── Ajuste manual de stock (SOLO ADMIN) ─────────────────

    /**
     * ⚠️ IMPORTANTE:
     * Este método es SOLO para ajustes manuales de inventario.
     * NO usar en flujo normal (ventas, compras, entregas).
     *
     * El flujo normal usa movimientos_inventario + trigger.
     */
    public Producto ajustarStock(UUID id, int cantidad) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        int nuevoStock = producto.getStock() + cantidad;

        if (nuevoStock < 0) {
            throw new IllegalStateException(
                "Stock insuficiente. Stock actual: " + producto.getStock());
        }

        producto.setStock(nuevoStock);
        return productoRepository.save(producto);
    }
}