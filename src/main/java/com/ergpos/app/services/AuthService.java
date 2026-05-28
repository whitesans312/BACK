package com.ergpos.app.services;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.UsuarioRepository;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> login(String email, String password) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        if (usuario.isEmpty() || !Boolean.TRUE.equals(usuario.get().getActivo())) {
            return Optional.empty();
        }

        Usuario encontrado = usuario.get();
        String passwordGuardada = encontrado.getPassword();

        if (isBcryptHash(passwordGuardada) && passwordEncoder.matches(password, passwordGuardada)) {
            return Optional.of(encontrado);
        }

        if (!isBcryptHash(passwordGuardada) && passwordGuardada.equals(password)) {
            encontrado.setPassword(passwordEncoder.encode(password));
            return Optional.of(usuarioRepository.save(encontrado));
        }

        return Optional.empty();
    }

    public Usuario register(Usuario usuario) {
        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new EmailAlreadyExistsException("El email ya está registrado: " + usuario.getEmail());
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    private boolean isBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String message) {
            super(message);
        }
    }
}
