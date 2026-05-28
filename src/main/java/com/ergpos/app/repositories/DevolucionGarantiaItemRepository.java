package com.ergpos.app.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.DevolucionGarantiaItem;

@Repository
public interface DevolucionGarantiaItemRepository extends JpaRepository<DevolucionGarantiaItem, UUID> {

    @Query("""
            SELECT COALESCE(SUM(i.cantidad), 0)
            FROM DevolucionGarantiaItem i
            WHERE i.devolucion.venta.id = :ventaId
              AND i.producto.id = :productoId
              AND i.devolucion.estado <> 'ANULADA'
            """)
    Long sumCantidadByVentaAndProducto(UUID ventaId, UUID productoId);

    @Query("""
            SELECT COALESCE(SUM(i.cantidad), 0)
            FROM DevolucionGarantiaItem i
            WHERE i.devolucion.entrega.id = :entregaId
              AND i.producto.id = :productoId
              AND i.devolucion.estado <> 'ANULADA'
            """)
    Long sumCantidadByEntregaAndProducto(UUID entregaId, UUID productoId);
}
