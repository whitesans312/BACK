package com.ergpos.app.controllers;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.UsuarioRepository;
import com.ergpos.app.security.JwtService;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AuditoriaService auditoriaService;
    private final UsuarioRepository usuarioRepository;

    public AuthController(AuthService authService, JwtService jwtService,
                          AuditoriaService auditoriaService,
                          UsuarioRepository usuarioRepository) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.auditoriaService = auditoriaService;
        this.usuarioRepository = usuarioRepository;
    }

    public static class LoginRequest {
        private String email;
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public record LoginResponse(Usuario user, String token, long expiresInMs) {}

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request) {
        Optional<Usuario> usuario = authService.login(request.getEmail(), request.getPassword());
        if (usuario.isPresent()) {
            String token = jwtService.generateToken(usuario.get());
            auditoriaService.registrar(
                usuario.get().getId(), usuario.get().getNombre(),
                "LOGIN_EXITOSO", "SEGURIDAD",
                usuario.get().getId(),
                "Inicio de sesion: " + usuario.get().getEmail()
            );
            return ResponseEntity.ok(new LoginResponse(usuario.get(), token, jwtService.getExpirationMs()));
        }
        auditoriaService.registrar(
            null, request.getEmail(),
            "LOGIN_FALLIDO", "SEGURIDAD",
            null,
            "Intento fallido de inicio de sesion: " + request.getEmail()
        );
        return ResponseEntity.status(401).body("Credenciales incorrectas");
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@Valid @RequestBody Usuario usuario) {
        try {
            Usuario creado = authService.register(usuario);
            auditoriaService.registrarActual(
                "CREAR_USUARIO", "USUARIOS",
                creado.getId(),
                "Usuario registrado: " + creado.getNombre() + " (" + creado.getEmail() + ")"
            );
            return ResponseEntity.ok(creado);
        } catch (AuthService.EmailAlreadyExistsException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/auth/refresh
     * Renueva el token JWT del usuario autenticado sin requerir contraseña.
     * El token actual debe ser válido para acceder a este endpoint (autenticado por el filtro JWT).
     */
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Usuario usuario)) {
            return ResponseEntity.status(401).body("No autenticado");
        }
        // Recargar desde BD para incluir posibles cambios de rol
        Optional<Usuario> fresh = usuarioRepository.findByEmail(usuario.getEmail());
        if (fresh.isEmpty() || !fresh.get().getActivo()) {
            return ResponseEntity.status(403).body("Usuario inactivo o no encontrado");
        }
        String newToken = jwtService.generateToken(fresh.get());
        return ResponseEntity.ok(new LoginResponse(fresh.get(), newToken, jwtService.getExpirationMs()));
    }

    /**
     * GET /api/auth/me
     * Devuelve los datos del usuario actualmente autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<Object> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Usuario usuario)) {
            return ResponseEntity.status(401).body("No autenticado");
        }
        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Usuario usuario) {
            auditoriaService.registrar(
                usuario.getId(), usuario.getNombre(),
                "LOGOUT", "SEGURIDAD",
                usuario.getId(),
                "Cierre de sesion: " + usuario.getEmail()
            );
        } else {
            auditoriaService.registrarActual("LOGOUT", "SEGURIDAD", null, "Cierre de sesion");
        }
        return ResponseEntity.noContent().build();
    }
}
