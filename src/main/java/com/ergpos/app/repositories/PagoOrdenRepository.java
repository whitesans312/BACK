package com.ergpos.app.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.PagoOrden;

@Repository
public interface PagoOrdenRepository extends JpaRepository<PagoOrden, UUID> {
    List<PagoOrden> findByEntregaId(UUID entregaId);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoOrden p WHERE p.entrega.id = :entregaId")
    BigDecimal sumMontoByEntregaId(UUID entregaId);
    @Query("SELECT COUNT(p) FROM PagoOrden p WHERE p.entrega.id = :entregaId")
    long countByEntregaId(UUID entregaId);
}