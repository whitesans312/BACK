// ── FacturaVentaRepository.java ───────────────────────────────────────────────
package com.ergpos.app.repositories;
 
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.FacturaVenta;
 
@Repository
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, UUID> {
    Optional<FacturaVenta> findByVentaId(UUID ventaId);
    Optional<FacturaVenta> findByNumero(String numero);
 
    // Obtener el ultimo numero para auto-incrementar
    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(f) FROM FacturaVenta f")
    Long countAll();
    
}