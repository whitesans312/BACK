package com.ergpos.app.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ergpos.app.model.Caja;

public interface CajaRepository extends JpaRepository<Caja, UUID> {
    
    List<Caja> findByEstadoOrderByFechaAperturaDesc(String estado);
    
    List<Caja> findByUsuarioIdOrderByFechaAperturaDesc(UUID usuarioId);
    
    @Query("SELECT c FROM Caja c WHERE c.estado = 'ABIERTA' ORDER BY c.fechaApertura DESC")
    List<Caja> findCajasAbiertas();
    
    @Query("SELECT c FROM Caja c WHERE c.usuario.id = ?1 AND c.estado = 'ABIERTA'")
    Caja findCajaAbiertaByUsuario(UUID usuarioId);

    @Query("SELECT c FROM Caja c WHERE c.usuario.id = ?1 AND c.estado = 'ABIERTA'")
    Caja findCajaAbertaByUsuario(UUID usuarioId);
    
    @Query("SELECT COALESCE(SUM(c.montoFinal - c.montoInicial), 0) FROM Caja c WHERE c.estado = 'CERRADA' AND c.fechaCierre BETWEEN ?1 AND ?2")
    java.math.BigDecimal sumSaldoCerradasEnRango(LocalDateTime desde, LocalDateTime hasta);
}
