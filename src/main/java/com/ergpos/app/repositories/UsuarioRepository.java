package com.ergpos.app.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    @Query("SELECT u FROM Usuario u LEFT JOIN FETCH u.rol WHERE u.email = :email")
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    // Obtener técnicos activos (para asignar a entregas)
    @Query("SELECT u FROM Usuario u JOIN u.rol r WHERE r.nombre IN ('TECNICO', 'ADMIN') AND u.activo = true")
List<Usuario> findTecnicosActivos();

    // ADMIN también puede ser técnico, traer todos los activos
    List<Usuario> findByActivoTrue();
}