package com.ergpos.app.repositories;

import com.ergpos.app.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
    Optional<Categoria> findByNombreIgnoreCase(String nombre);

    /** Cuenta cuántos productos ACTIVOS hay en la categoría */
    @Query("SELECT COUNT(p) FROM Producto p WHERE p.categoria.id = :catId AND p.activo = true")
    long countProductosActivos(@Param("catId") UUID catId);
}
