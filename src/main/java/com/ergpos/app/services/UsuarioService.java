package com.ergpos.app.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.UsuarioRepository;

/**
 * Servicio de negocio para la gestión de usuarios del sistema.
 * <p>
 * Gestiona el ciclo de vida de los usuarios: creación, consulta, activación/desactivación
 * y cambio de contraseña con codificación BCrypt automática. Garantiza unicidad de email
 * mediante validación antes de persistir.
 * </p>
 *
 * @author ERG-POS Dev Team
 * @since 1.0
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Retorna todos los usuarios registrados en el sistema (activos e inactivos).
     *
     * @return lista completa de {@link Usuario}
     */
    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    /**
     * Retorna únicamente los usuarios con estado activo.
     *
     * @return lista de {@link Usuario} cuyo campo {@code activo} es {@code true}
     */
    public List<Usuario> findActivos() {
        return usuarioRepository.findByActivoTrue();
    }

    /**
     * Retorna técnicos activos disponibles para asignar a órdenes de entrega.
     * Incluye usuarios con rol TECNICO y ADMIN (polivalentes).
     *
     * @return lista de técnicos activos
     */
    public List<Usuario> findTecnicosActivos() {
        return usuarioRepository.findTecnicosActivos();
    }

    /**
     * Busca un usuario por su identificador único.
     *
     * @param id UUID del usuario
     * @return {@link Optional} con el usuario si existe
     */
    public Optional<Usuario> findById(UUID id) {
        return usuarioRepository.findById(id);
    }

    /**
     * Busca un usuario por su dirección de correo electrónico.
     *
     * @param email dirección de email (case-insensitive)
     * @return {@link Optional} con el usuario si existe
     */
    public Optional<Usuario> findByEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    /**
     * Verifica si ya existe un usuario registrado con el email dado.
     *
     * @param email dirección de email a verificar
     * @return {@code true} si el email ya está en uso
     */
    public boolean existsByEmail(String email) {
        return email != null && !email.isBlank()
                && usuarioRepository.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * Persiste o actualiza un usuario aplicando normalización de datos y codificación BCrypt
     * de la contraseña si aún no está hasheada.
     *
     * @param usuario entidad a guardar/actualizar
     * @return el usuario persistido con todos sus campos actualizados
     * @throws IllegalArgumentException si el email ya está en uso por otro usuario
     */
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
