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

import com.ergpos.app.model.Cliente;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.ClienteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final AuditoriaService auditoriaService;

    public ClienteController(ClienteService clienteService,
                             AuditoriaService auditoriaService) {
        this.clienteService = clienteService;
        this.auditoriaService = auditoriaService;
    }

    // GET /api/clientes
    @GetMapping
    public List<Cliente> getAll() {
        return clienteService.findAll();
    }

    // GET /api/clientes/activos
    @GetMapping("/activos")
    public List<Cliente> getActivos() {
        return clienteService.findActivos();
    }

    // GET /api/clientes/buscar?q=texto
    @GetMapping("/buscar")
    public List<Cliente> buscar(@RequestParam String q) {
        return clienteService.buscar(q);
    }

    // GET /api/clientes/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Cliente> getById(@PathVariable UUID id) {
        return clienteService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/perfil")
    public ResponseEntity<Object> getPerfilCompleto(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(clienteService.getPerfilCompleto(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // POST /api/clientes
    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody Cliente cliente) {
        // Convertir strings vacíos a null para evitar violaciones UNIQUE en BD
        if (cliente.getTelefono() != null && cliente.getTelefono().isBlank()) cliente.setTelefono(null);
        if (cliente.getEmail() != null && cliente.getEmail().isBlank()) cliente.setEmail(null);
        if (cliente.getDocumento() != null && cliente.getDocumento().isBlank()) cliente.setDocumento(null);

        if (clienteService.existsByTelefono(cliente.getTelefono())) {
            return ResponseEntity.badRequest().body("Ya existe un cliente con ese teléfono");
        }
        if (clienteService.existsByEmail(cliente.getEmail())) {
            return ResponseEntity.badRequest().body("Ya existe un cliente con ese email");
        }
        try {
            Cliente guardado = clienteService.save(cliente);
            auditoriaService.registrarActual(
                "CREAR_CLIENTE", "CLIENTES",
                guardado.getId(),
                "Cliente creado: " + guardado.getNombre()
            );
            return ResponseEntity.ok(guardado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al guardar el cliente: " + e.getMessage());
        }
    }

    // PUT /api/clientes/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id,
                                         @Valid @RequestBody Cliente det) {
        Optional<Cliente> opt = clienteService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        // Convertir strings vacíos a null para evitar violaciones UNIQUE
        if (det.getTelefono() != null && det.getTelefono().isBlank()) det.setTelefono(null);
        if (det.getEmail() != null && det.getEmail().isBlank()) det.setEmail(null);
        if (det.getDocumento() != null && det.getDocumento().isBlank()) det.setDocumento(null);

        Cliente c = opt.get();
        c.setNombre(det.getNombre());
        c.setTelefono(det.getTelefono());
        c.setEmail(det.getEmail());
        c.setDireccion(det.getDireccion());
        c.setBarrio(det.getBarrio());
        c.setNotas(det.getNotas());
        c.setDocumento(det.getDocumento());
        c.setCiudad(det.getCiudad());
        try {
            Cliente actualizado = clienteService.save(c);
            auditoriaService.registrarActual(
                "EDITAR_CLIENTE", "CLIENTES",
                actualizado.getId(),
                "Cliente editado: " + actualizado.getNombre()
            );
            return ResponseEntity.ok(actualizado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al actualizar el cliente: " + e.getMessage());
        }
    }

    // DELETE /api/clientes/{id}  →  soft delete
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        Optional<Cliente> opt = clienteService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Cliente c = opt.get();
        c.setActivo(false);
        Cliente actualizado = clienteService.save(c);
        auditoriaService.registrarActual(
            "DESACTIVAR_CLIENTE", "CLIENTES",
            actualizado.getId(),
            "Cliente desactivado: " + actualizado.getNombre()
        );
        return ResponseEntity.ok("Cliente desactivado correctamente");
    }

    // PATCH /api/clientes/{id}/activar?activo=true|false
    @PatchMapping("/{id}/activar")
    public ResponseEntity<Cliente> toggleActivo(@PathVariable UUID id,
                                                @RequestParam boolean activo) {
        Optional<Cliente> opt = clienteService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Cliente c = opt.get();
        c.setActivo(activo);
        Cliente actualizado = clienteService.save(c);
        auditoriaService.registrarActual(
            activo ? "ACTIVAR_CLIENTE" : "DESACTIVAR_CLIENTE", "CLIENTES",
            actualizado.getId(),
            (activo ? "Cliente activado: " : "Cliente desactivado: ") + actualizado.getNombre()
        );
        return ResponseEntity.ok(actualizado);
    }
}
