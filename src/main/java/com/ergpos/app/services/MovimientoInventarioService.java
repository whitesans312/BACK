package com.ergpos.app.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.repositories.MovimientoInventarioRepository;

@Service
public class MovimientoInventarioService {

    private final MovimientoInventarioRepository movimientoRepository;
    private final AuditoriaService auditoriaService;

    public MovimientoInventarioService(MovimientoInventarioRepository movimientoRepository,
                                       AuditoriaService auditoriaService) {
        this.movimientoRepository = movimientoRepository;
        this.auditoriaService     = auditoriaService;
    }

    public List<MovimientoInventario> findAll() {
        return movimientoRepository.findAll();
    }

    public List<MovimientoInventario> findRecientes() {
        return movimientoRepository.findTop10ByOrderByFechaDesc();
    }

    // Registrar ENTRADA de stock — el trigger fn_actualizar_stock() de la BD suma al stock
    @Transactional
    public MovimientoInventario registrarEntrada(MovimientoInventario movimiento) {
        movimiento.setTipo("ENTRADA");
        movimiento.setOrigenTipo("MANUAL");
        MovimientoInventario saved = movimientoRepository.save(movimiento);

        // Construir detalle legible para auditoría
        String productoNombre = saved.getProducto() != null ? saved.getProducto().getNombre() : "Producto";
        String proveedorNombre = saved.getProveedor() != null ? " — Proveedor: " + saved.getProveedor().getNombre() : "";
        String detalle = "Entrada de " + saved.getCantidad() + " u. de " + productoNombre + proveedorNombre;

        // Usuario que hizo la acción (puede ser null si no se envía)
        java.util.UUID usuarioId   = saved.getUsuario() != null ? saved.getUsuario().getId()   : null;
        String         usuarioNom  = saved.getUsuario() != null ? saved.getUsuario().getNombre() : null;

        auditoriaService.registrar(usuarioId, usuarioNom, "ENTRADA_INVENTARIO", "INVENTARIO",
                saved.getId(), detalle);

        return saved;
    }

    // Registrar SALIDA de stock — el trigger fn_actualizar_stock() de la BD resta del stock
    @Transactional
    public MovimientoInventario registrarSalida(MovimientoInventario movimiento) {
        movimiento.setTipo("SALIDA");
        movimiento.setOrigenTipo("MANUAL");
        MovimientoInventario saved = movimientoRepository.save(movimiento);

        String productoNombre = saved.getProducto() != null ? saved.getProducto().getNombre() : "Producto";
        String detalle = "Salida manual de " + saved.getCantidad() + " u. de " + productoNombre;

        java.util.UUID usuarioId  = saved.getUsuario() != null ? saved.getUsuario().getId()    : null;
        String         usuarioNom = saved.getUsuario() != null ? saved.getUsuario().getNombre() : null;

        auditoriaService.registrar(usuarioId, usuarioNom, "SALIDA_INVENTARIO", "INVENTARIO",
                saved.getId(), detalle);

        return saved;
    }
}