package com.ergpos.app.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ergpos.app.model.MovimientoCaja;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, UUID> {
    
    List<MovimientoCaja> findByCajaIdOrderByFechaDesc(UUID cajaId, Pageable pageRequest);
    
    List<MovimientoCaja> findByTipoOrderByFechaDesc(String tipo, Pageable pageRequest);
    
    @Query("SELECT m FROM MovimientoCaja m WHERE m.caja.id = ?1 AND m.fecha BETWEEN ?2 AND ?3 ORDER BY m.fecha DESC")
    List<MovimientoCaja> findByCajaAndFechaBetween(UUID cajaId, LocalDateTime desde, LocalDateTime hasta);
    
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo IN ('INGRESO', 'PAGO_VENTA', 'PAGO_ORDEN') AND m.fecha BETWEEN ?1 AND ?2")
    java.math.BigDecimal sumIngresosEnRango(LocalDateTime desde, LocalDateTime hasta);
    
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo IN ('EGRESO', 'COMPRA', 'DEVOLUCION') AND m.fecha BETWEEN ?1 AND ?2")
    java.math.BigDecimal sumEgresosEnRango(LocalDateTime desde, LocalDateTime hasta);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.caja.id = ?1 AND m.tipo IN ('INGRESO', 'PAGO_VENTA', 'PAGO_ORDEN')")
    java.math.BigDecimal sumIngresosByCaja(UUID cajaId);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.caja.id = ?1 AND m.tipo IN ('EGRESO', 'COMPRA', 'DEVOLUCION')")
    java.math.BigDecimal sumEgresosByCaja(UUID cajaId);
}
