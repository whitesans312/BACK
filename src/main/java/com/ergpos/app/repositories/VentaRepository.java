package com.ergpos.app.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Venta;

@Repository
public interface VentaRepository extends JpaRepository<Venta, UUID> {

    List<Venta> findByEstado(String estado);

    List<Venta> findByVendedorId(UUID vendedorId);

    List<Venta> findByClienteIdOrderByFechaDesc(UUID clienteId);

    /** Busca ventas por cliente FK o por teléfono snapshot (transacciones sin FK). */
    @Query("SELECT DISTINCT v FROM Venta v WHERE v.cliente.id = :clienteId OR v.clienteTelefono = :telefono ORDER BY v.fecha DESC")
    List<Venta> findByClienteIdOrTelefonoOrderByFechaDesc(
            @Param("clienteId") UUID clienteId,
            @Param("telefono") String telefono);

    // Ventas del día
    @Query("SELECT v FROM Venta v WHERE v.fecha >= :inicio AND v.fecha < :fin ORDER BY v.fecha DESC")
    List<Venta> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);

    // Contar solo ventas COMPLETADAS en rango (excluye CANCELADAS)
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.estado = 'COMPLETADA' AND v.fecha >= :inicio AND v.fecha < :fin")
    long countCompletadasEnRango(LocalDateTime inicio, LocalDateTime fin);
    // Total ingresos de ventas completadas
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.estado = 'COMPLETADA'")
    Double sumTotalCompletadas();

    // Total ingresos en rango de fechas
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.estado = 'COMPLETADA' AND v.fecha >= :inicio AND v.fecha < :fin")
    Double sumTotalCompletadasEnRango(LocalDateTime inicio, LocalDateTime fin);

    long countByEstado(String estado);

    // Últimas ventas para dashboard
    List<Venta> findTop10ByOrderByFechaDesc();

    // Ventas por vendedor y estado
    long countByVendedorIdAndEstado(UUID vendedorId, String estado);

    @Query("SELECT vi.producto.nombre, SUM(vi.cantidad) FROM VentaItem vi WHERE vi.venta.estado = 'COMPLETADA' GROUP BY vi.producto.nombre ORDER BY SUM(vi.cantidad) DESC")
    List<Object[]> findProductosMasVendidos();

    @Query("""
           SELECT COALESCE(v.cliente.nombre, v.clienteNombre), COALESCE(v.cliente.telefono, v.clienteTelefono),
                  COUNT(v), COALESCE(SUM(v.total), 0)
           FROM Venta v
           WHERE v.estado = 'COMPLETADA'
           GROUP BY COALESCE(v.cliente.nombre, v.clienteNombre), COALESCE(v.cliente.telefono, v.clienteTelefono)
           ORDER BY COALESCE(SUM(v.total), 0) DESC
           """)
    List<Object[]> findClientesConMasCompras();
}
