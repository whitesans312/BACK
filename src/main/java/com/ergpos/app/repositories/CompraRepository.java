// ── CompraRepository.java ─────────────────────────────────────────────────────
package com.ergpos.app.repositories;
 
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Compra;
 
@Repository
public interface CompraRepository extends JpaRepository<Compra, UUID> {
    List<Compra> findByEstado(String estado);
    List<Compra> findByProveedorId(UUID proveedorId);
    List<Compra> findTop10ByOrderByFechaDesc();

    @Query("SELECT COALESCE(SUM(c.total), 0) FROM Compra c WHERE c.estado = 'CONFIRMADA'")
    Double sumTotalConfirmadas();

    @Query("SELECT COALESCE(SUM(c.total), 0) FROM Compra c WHERE c.estado = 'CONFIRMADA' AND c.fecha >= :inicio AND c.fecha < :fin")
    Double sumTotalConfirmadasEnRango(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}