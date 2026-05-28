package com.ergpos.app.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.FacturaOrden;

@Repository
public interface FacturaOrdenRepository extends JpaRepository<FacturaOrden, UUID> {
    Optional<FacturaOrden> findByEntregaId(UUID entregaId);
    boolean existsByEntregaId(UUID entregaId);

    @Query("SELECT COUNT(f) FROM FacturaOrden f")
    Long countAll();
}