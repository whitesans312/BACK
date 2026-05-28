package com.ergpos.app.controllers;

import com.ergpos.app.model.Categoria;
import com.ergpos.app.repositories.CategoriaRepository;
import com.ergpos.app.services.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoriaService;

    public CategoriaController(CategoriaRepository categoriaRepository,
                               AuditoriaService auditoriaService) {
        this.categoriaRepository = categoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    /** Lista todas las categorías activas (uso general: dropdowns de productos) */
    @GetMapping
    public List<Categoria> getAll() {
        return categoriaRepository.findAll()
                .stream()
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .toList();
    }

    /** Lista TODAS las categorías (activas e inactivas) para el panel de gestión */
    @GetMapping("/todas")
    public List<Categoria> getTodas() {
        return categoriaRepository.findAll();
    }

    /** Crea una categoría nueva si el nombre no existe */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Categoria categoria) {
        boolean exists = categoriaRepository.findByNombreIgnoreCase(categoria.getNombre()).isPresent();
        if (exists) {
            return ResponseEntity.badRequest().body("Ya existe una categoría con ese nombre.");
        }
        categoria.setActivo(true);
        Categoria guardada = categoriaRepository.save(categoria);
        auditoriaService.registrarActual(
            "CREAR_CATEGORIA", "CATEGORIAS",
            guardada.getId(),
            "Categoria creada: " + guardada.getNombre()
        );
        return ResponseEntity.ok(guardada);
    }

    /** Edita nombre y descripción de una categoría */
    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id, @RequestBody Categoria datos) {
        Optional<Categoria> opt = categoriaRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();

        Categoria cat = opt.get();

        // Verificar nombre único (excluyendo la propia categoría)
        Optional<Categoria> existing = categoriaRepository.findByNombreIgnoreCase(datos.getNombre());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body("Ya existe una categoría con ese nombre.");
        }

        cat.setNombre(datos.getNombre().trim());
        cat.setDescripcion(datos.getDescripcion());
        Categoria actualizada = categoriaRepository.save(cat);
        auditoriaService.registrarActual(
            "EDITAR_CATEGORIA", "CATEGORIAS",
            actualizada.getId(),
            "Categoria editada: " + actualizada.getNombre()
        );
        return ResponseEntity.ok(actualizada);
    }

    /** Activa o desactiva una categoría */
    @PatchMapping("/{id}/activo")
    public ResponseEntity<Object> toggleActivo(@PathVariable UUID id, @RequestParam boolean activo) {
        Optional<Categoria> opt = categoriaRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();

        // Si se intenta desactivar, verificar que no tenga productos activos
        if (!activo) {
            long productosActivos = categoriaRepository.countProductosActivos(id);
            if (productosActivos > 0) {
                return ResponseEntity.badRequest()
                        .body("No se puede desactivar: la categoría tiene " + productosActivos +
                                " producto(s) activo(s) con stock.");
            }
        }

        Categoria cat = opt.get();
        cat.setActivo(activo);
        Categoria actualizada = categoriaRepository.save(cat);
        auditoriaService.registrarActual(
            activo ? "ACTIVAR_CATEGORIA" : "DESACTIVAR_CATEGORIA", "CATEGORIAS",
            actualizada.getId(),
            (activo ? "Categoria activada: " : "Categoria desactivada: ") + actualizada.getNombre()
        );
        return ResponseEntity.ok(actualizada);
    }

    /**
     * Eliminación lógica (marca activo=false). Bloqueada si hay productos activos.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable UUID id) {
        Optional<Categoria> opt = categoriaRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();

        long productosActivos = categoriaRepository.countProductosActivos(id);
        if (productosActivos > 0) {
            return ResponseEntity.badRequest()
                    .body("No se puede eliminar: la categoría tiene " + productosActivos +
                            " producto(s) activo(s). Reasigna o desactiva esos productos primero.");
        }

        Categoria cat = opt.get();
        cat.setActivo(false);
        Categoria actualizada = categoriaRepository.save(cat);
        auditoriaService.registrarActual(
            "DESACTIVAR_CATEGORIA", "CATEGORIAS",
            actualizada.getId(),
            "Categoria eliminada/desactivada: " + actualizada.getNombre()
        );
        return ResponseEntity.ok("Categoría eliminada correctamente.");
    }
}
