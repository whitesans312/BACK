package com.ergpos.app.controllers;

import java.util.List;
import java.util.Map;
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

import com.ergpos.app.model.Rol;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService    usuarioService;
    private final AuditoriaService  auditoriaService;

    public UsuarioController(UsuarioService usuarioService,
                             AuditoriaService auditoriaService) {
        this.usuarioService   = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<Usuario> getAll() {
        return usuarioService.findAll();
    }

    @GetMapping("/activos")
    public List<Usuario> getActivos() {
        return usuarioService.findActivos();
    }

    // Para asignar técnicos a entregas
    @GetMapping("/tecnicos")
    public List<Usuario> getTecnicos() {
        return usuarioService.findTecnicosActivos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getById(@PathVariable UUID id) {
        return usuarioService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Usuario> getByEmail(@PathVariable String email) {
        return usuarioService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST - Crear usuario directamente (sin pasar por /auth/register)
    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody Usuario usuario) {
        if (usuarioService.existsByEmail(usuario.getEmail())) {
            return ResponseEntity.badRequest().body("El email ya está registrado");
        }
        try {
            Usuario guardado = usuarioService.save(usuario);
            auditoriaService.registrar(
                null, "Admin",
                "CREAR_USUARIO", "USUARIOS",
                guardado.getId(),
                "Usuario creado: " + guardado.getNombre() + " (" + guardado.getEmail() + ")"
            );
            return ResponseEntity.ok(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id, @Valid @RequestBody Usuario usuarioDetails) {
        Optional<Usuario> opt = usuarioService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Usuario existing = opt.get();
        existing.setNombre(usuarioDetails.getNombre());
        existing.setEmail(usuarioDetails.getEmail());
        existing.setTelefono(usuarioDetails.getTelefono());

        if (usuarioDetails.getPassword() != null && !usuarioDetails.getPassword().isBlank()) {
            existing.setPassword(usuarioDetails.getPassword());
        }

        if (usuarioDetails.getRol() != null && usuarioDetails.getRol().getId() != null) {
            Rol nuevoRol = new Rol();
            nuevoRol.setId(usuarioDetails.getRol().getId());
            existing.setRol(nuevoRol);
        }

        try {
            Usuario actualizado = usuarioService.save(existing);
            auditoriaService.registrar(
                null, "Admin",
                "EDITAR_USUARIO", "USUARIOS",
                actualizado.getId(),
                "Usuario editado: " + actualizado.getNombre() + " (" + actualizado.getEmail() + ")"
            );
            return ResponseEntity.ok(actualizado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        Optional<Usuario> opt = usuarioService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Usuario u = opt.get();
        u.setActivo(false);
        usuarioService.save(u);
        auditoriaService.registrarActual(
            "DESACTIVAR_USUARIO", "USUARIOS",
            u.getId(),
            "Usuario desactivado: " + u.getNombre() + " (" + u.getEmail() + ")"
        );
        return ResponseEntity.ok("Usuario desactivado correctamente");
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<Usuario> toggleActivo(@PathVariable UUID id, @RequestParam boolean activo) {
        Optional<Usuario> opt = usuarioService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Usuario u = opt.get();
        u.setActivo(activo);
        Usuario actualizado = usuarioService.save(u);
        auditoriaService.registrarActual(
            activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO", "USUARIOS",
            actualizado.getId(),
            (activo ? "Usuario activado: " : "Usuario desactivado: ")
            + actualizado.getNombre() + " (" + actualizado.getEmail() + ")"
        );
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<String> cambiarPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        Optional<Usuario> opt = usuarioService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Usuario u = opt.get();
        u.setPassword(body.get("password"));
        usuarioService.save(u);
        auditoriaService.registrarActual(
            "CAMBIAR_PASSWORD_USUARIO", "USUARIOS",
            u.getId(),
            "Password actualizado para usuario: " + u.getNombre() + " (" + u.getEmail() + ")"
        );
        return ResponseEntity.ok("Contraseña actualizada correctamente");
    }
}
