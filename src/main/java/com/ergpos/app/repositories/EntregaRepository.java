package com.ergpos.app.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Entrega;

@Repository
public interface EntregaRepository extends JpaRepository<Entrega, UUID> {

    /**
     * Suma únicamente la mano de obra de órdenes FINALIZADAS.
     * Los repuestos ya se contabilizan en totalVentasPOS (via Venta generada al confirmar),
     * por lo que sumar totalOrden causaría doble conteo.
     */
    @Query("SELECT COALESCE(SUM(e.manoObra), 0) FROM Entrega e WHERE e.estado = 'FINALIZADO'")
    Double sumTotalFinalizadas();

    /**
     * Suma mano de obra de órdenes finalizadas en un rango.
     * Usa fechaEntrega (momento real de cierre) en lugar de fechaCreacion.
     */
    @Query("SELECT COALESCE(SUM(e.manoObra), 0) FROM Entrega e WHERE e.estado = 'FINALIZADO' AND e.fechaEntrega >= :inicio AND e.fechaEntrega < :fin")
    Double sumTotalFinalizadasEnRango(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    List<Entrega> findByEstado(String estado);

    @Query("SELECT e FROM Entrega e WHERE e.tecnico.id = :tecnicoId")
    List<Entrega> findByTecnicoId(UUID tecnicoId);

    List<Entrega> findByClienteIdOrderByFechaCreacionDesc(UUID clienteId);

    /** Busca órdenes por cliente FK o por teléfono snapshot (registros sin FK). */
    @Query("SELECT DISTINCT e FROM Entrega e WHERE e.cliente.id = :clienteId OR e.clienteTelefono = :telefono ORDER BY e.fechaCreacion DESC")
    List<Entrega> findByClienteIdOrTelefonoOrderByFechaCreacionDesc(
            @Param("clienteId") UUID clienteId,
            @Param("telefono") String telefono);

    @Query("SELECT e FROM Entrega e WHERE e.tecnico.id = :tecnicoId AND e.estado = 'PENDIENTE'")
    List<Entrega> findPendientesByTecnicoId(UUID tecnicoId);

    long countByEstado(String estado);

    // Órdenes con pago pendiente/parcial
    List<Entrega> findByEstadoPagoIn(List<String> estadosPago);

    long countByEstadoPagoIn(List<String> estadosPago);

    // Excluye CANCELADAS: una orden cancelada con pago pendiente no es una alerta real
    long countByEstadoPagoInAndEstadoNot(List<String> estadosPago, String estado);

    // Órdenes en un estado concreto con pago no completo (ej: FINALIZADO + PENDIENTE)
    long countByEstadoAndEstadoPagoIn(String estado, List<String> estadosPago);
}
