package com.ergpos.app.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    public List<Usuario> findActivos() {
        return usuarioRepository.findByActivoTrue();
    }

    // Para asignar a entregas: técnicos activos (incluye admins que hacen de todo)
    public List<Usuario> findTecnicosActivos() {
        return usuarioRepository.findTecnicosActivos();
    }

    public Optional<Usuario> findById(UUID id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> findByEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return email != null && !email.isBlank()
                && usuarioRepository.existsByEmail(email.trim().toLowerCase());
    }

    public Usuario save(Usuario usuario) {
        normalizar(usuario);
        validarEmailUnico(usuario);

        if (usuario.getPassword() != null && !usuario.getPassword().isBlank() && !isBcryptHash(usuario.getPassword())) {
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        }
        usuario.setUpdatedAt(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private boolean isBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private void normalizar(Usuario usuario) {
        if (usuario.getNombre() != null) {
            usuario.setNombre(usuario.getNombre().trim());
        }
        if (usuario.getEmail() != null) {
            usuario.setEmail(usuario.getEmail().trim().toLowerCase());
        }
        if (usuario.getTelefono() != null) {
            String telefono = usuario.getTelefono().trim();
            usuario.setTelefono(telefono.isBlank() ? null : telefono);
        }
    }

    private void validarEmailUnico(Usuario usuario) {
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return;
        }

        Optional<Usuario> existente = usuarioRepository.findByEmail(usuario.getEmail());
        if (existente.isPresent()
                && (usuario.getId() == null || !existente.get().getId().equals(usuario.getId()))) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }
    }
}
