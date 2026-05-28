package com.ergpos.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "configuracion_negocio")
public class ConfiguracionNegocio {

    @Id
    @Column(nullable = false, length = 50)
    private String clave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(nullable = false, length = 30)
    private String categoria;

    @Column(length = 255)
    private String descripcion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
