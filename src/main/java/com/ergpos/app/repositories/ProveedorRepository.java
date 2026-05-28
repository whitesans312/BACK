// ── ProveedorRepository.java ──────────────────────────────────────────────────
package com.ergpos.app.repositories;
 
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Proveedor;
 
@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {
    List<Proveedor> findByActivoTrue();
    Optional<Proveedor> findByNit(String nit);
    boolean existsByNit(String nit);
 
    @Query("SELECT p FROM Proveedor p WHERE p.activo = true AND (" +
           "LOWER(p.nombre) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(COALESCE(p.nit,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(COALESCE(p.contacto,'')) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<Proveedor> buscar(String q);
}