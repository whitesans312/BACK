package com.ergpos.app.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByTelefono(String telefono);

    Optional<Cliente> findByEmail(String email);

    boolean existsByTelefono(String telefono);

    boolean existsByEmail(String email);

    List<Cliente> findByActivoTrue();

    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND (" +
           "LOWER(c.nombre)    LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(c.telefono)  LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(COALESCE(c.email,'')) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<Cliente> buscar(String q);
}