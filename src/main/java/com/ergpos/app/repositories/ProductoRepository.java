package com.ergpos.app.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Categoria;
import com.ergpos.app.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    Optional<Producto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Producto> findByActivoTrue();

    // categoria ahora es FK a Categoria (era String libre)
    List<Producto> findByCategoria(Categoria categoria);

    // También útil: buscar por el UUID de la categoría directamente
    List<Producto> findByCategoriaId(UUID categoriaId);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stock <= p.stockMinimo")
    List<Producto> findProductosConStockBajo();

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND " +
           "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.codigo) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Producto> buscarPorNombreOCodigo(String query);
}