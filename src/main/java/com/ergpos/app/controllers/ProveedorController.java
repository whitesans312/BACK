// ── ProveedorController.java ──────────────────────────────────────────────────
package com.ergpos.app.controllers;
 
import java.util.List;
import java.util.Optional;
import java.util.UUID;
 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Proveedor;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.ProveedorService;

import jakarta.validation.Valid;
 
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {
 
    private final ProveedorService service;
    private final AuditoriaService auditoriaService;

    public ProveedorController(ProveedorService service,
                               AuditoriaService auditoriaService) {
        this.service = service;
        this.auditoriaService = auditoriaService;
    }
 
    @GetMapping
    public List<Proveedor> getAll() { return service.findAll(); }
 
    @GetMapping("/activos")
    public List<Proveedor> getActivos() { return service.findActivos(); }
 
    @GetMapping("/buscar")
    public List<Proveedor> buscar(@RequestParam String q) { return service.buscar(q); }
 
    @GetMapping("/{id}")
    public ResponseEntity<Proveedor> getById(@PathVariable UUID id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
 
    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody Proveedor p) {
        if (service.existsByNit(p.getNit()))
            return ResponseEntity.badRequest().body("Ya existe un proveedor con ese NIT");
        Proveedor guardado = service.save(p);
        auditoriaService.registrarActual(
            "CREAR_PROVEEDOR", "PROVEEDORES",
            guardado.getId(),
            "Proveedor creado: " + guardado.getNombre()
        );
        return ResponseEntity.ok(guardado);
    }
 
    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id,
                                          @Valid @RequestBody Proveedor det) {
        Optional<Proveedor> opt = service.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Proveedor p = opt.get();
        p.setNombre(det.getNombre());
        p.setNit(det.getNit());
        p.setTelefono(det.getTelefono());
        p.setEmail(det.getEmail());
        p.setDireccion(det.getDireccion());
        p.setCiudad(det.getCiudad());
        p.setContacto(det.getContacto());
        p.setNotas(det.getNotas());
        Proveedor actualizado = service.save(p);
        auditoriaService.registrarActual(
            "EDITAR_PROVEEDOR", "PROVEEDORES",
            actualizado.getId(),
            "Proveedor editado: " + actualizado.getNombre()
        );
        return ResponseEntity.ok(actualizado);
    }
 
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        Optional<Proveedor> opt = service.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Proveedor p = opt.get();
        p.setActivo(false);
        Proveedor actualizado = service.save(p);
        auditoriaService.registrarActual(
            "DESACTIVAR_PROVEEDOR", "PROVEEDORES",
            actualizado.getId(),
            "Proveedor desactivado: " + actualizado.getNombre()
        );
        return ResponseEntity.ok("Proveedor desactivado");
    }
 
    @PatchMapping("/{id}/activar")
    public ResponseEntity<Proveedor> toggleActivo(@PathVariable UUID id,
                                                    @RequestParam boolean activo) {
        Optional<Proveedor> opt = service.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Proveedor p = opt.get();
        p.setActivo(activo);
        Proveedor actualizado = service.save(p);
        auditoriaService.registrarActual(
            activo ? "ACTIVAR_PROVEEDOR" : "DESACTIVAR_PROVEEDOR", "PROVEEDORES",
            actualizado.getId(),
            (activo ? "Proveedor activado: " : "Proveedor desactivado: ") + actualizado.getNombre()
        );
        return ResponseEntity.ok(actualizado);
    }
}
