package com.ergpos.app.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.DevolucionGarantia;

@Repository
public interface DevolucionGarantiaRepository extends JpaRepository<DevolucionGarantia, UUID> {
    List<DevolucionGarantia> findByVentaIdOrderByFechaDesc(UUID ventaId);
    List<DevolucionGarantia> findByEntregaIdOrderByFechaDesc(UUID entregaId);
    List<DevolucionGarantia> findByClienteIdOrderByFechaDesc(UUID clienteId);
    List<DevolucionGarantia> findByTipoOrderByFechaDesc(String tipo);
    List<DevolucionGarantia> findTop20ByOrderByFechaDesc();

    @Query("SELECT DISTINCT d FROM DevolucionGarantia d WHERE d.cliente.id = :clienteId OR (d.venta IS NOT NULL AND d.venta.clienteTelefono = :telefono) ORDER BY d.fecha DESC")
    List<DevolucionGarantia> findByClienteIdOrTelefonoOrderByFechaDesc(
        @Param("clienteId") UUID clienteId,
        @Param("telefono") String telefono
    );

    @Query("SELECT COALESCE(SUM(d.montoDevuelto), 0) FROM DevolucionGarantia d WHERE d.estado = 'REGISTRADA' OR d.estado = 'PROCESADA'")
    Double sumTotalDevuelto();

    @Query("SELECT COALESCE(SUM(d.montoDevuelto), 0) FROM DevolucionGarantia d WHERE (d.estado = 'REGISTRADA' OR d.estado = 'PROCESADA') AND d.fecha >= :inicio AND d.fecha < :fin")
    Double sumTotalDevueltoEnRango(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(d) FROM DevolucionGarantia d WHERE d.venta.id = :ventaId AND d.estado <> 'ANULADA'")
    long countActivasByVentaId(@Param("ventaId") UUID ventaId);

    @Query("SELECT COUNT(d) FROM DevolucionGarantia d WHERE d.entrega.id = :entregaId AND d.estado <> 'ANULADA'")
    long countActivasByEntregaId(@Param("entregaId") UUID entregaId);
}
