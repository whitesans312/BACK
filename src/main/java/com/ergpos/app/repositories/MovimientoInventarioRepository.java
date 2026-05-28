package com.ergpos.app.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.MovimientoInventario;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, UUID> {

    List<MovimientoInventario> findByProductoId(UUID productoId);

    List<MovimientoInventario> findByTipo(String tipo);

    List<MovimientoInventario> findTop10ByOrderByFechaDesc();

    // Agregado Sprint 3 — para Kardex ordenado por fecha ascendente
    long countByUsuarioId(UUID usuarioId);

    List<MovimientoInventario> findByProductoIdOrderByFechaAsc(UUID productoId);
}
