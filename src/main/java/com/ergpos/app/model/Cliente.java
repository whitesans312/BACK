package com.ergpos.app.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "El nombre es obligatorio")
    @Pattern(
        regexp = "^[\\p{L}]+(?:[ '\\-][\\p{L}]+)*$",
        message = "El nombre solo debe contener letras y espacios"
    )
    @Column(length = 120)
    private String nombre;

    @Pattern(
        regexp = "^$|^[0-9]{5,30}$",
        message = "El documento solo debe contener numeros"
    )
    @Column(length = 30)
    private String documento;

    @Pattern(
        regexp = "^[0-9]{7,20}$",
        message = "El telefono solo debe contener numeros"
    )
    @Column(unique = true, length = 20)
    private String telefono;

    @Email(message = "Email inválido")
    @Column(unique = true, length = 120)
    private String email;

    @Column(length = 200)
    private String direccion;

    @Column(length = 80)
    private String barrio;

    @Column(length = 80)
    private String ciudad = "Cali";

    @Column(columnDefinition = "TEXT")
    private String notas;

    private Boolean activo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Lógica de negocio ─────────────────────────────

    public boolean tieneContacto() {
        return (this.telefono != null && !this.telefono.isBlank())
            || (this.email != null && !this.email.isBlank());
    }

    // ── Hooks ────────────────────────────────────────

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        normalizar();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        normalizar();
    }

    private void normalizar() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
        if (this.nombre != null) {
            this.nombre = this.nombre.trim();
        }
        if (this.telefono != null) {
            this.telefono = this.telefono.trim();
        }
        if (this.documento != null) {
            this.documento = this.documento.trim();
        }
    }
}
