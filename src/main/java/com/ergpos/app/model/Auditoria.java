package com.ergpos.app.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nombre", length = 120)
    private String usuarioNombre;

    @Column(nullable = false, length = 60)
    private String accion;

    @Column(nullable = false, length = 30)
    private String modulo;

    @Column(name = "entidad_tipo", length = 40)
    private String entidadTipo;

    @Column(name = "entidad_id")
    private UUID entidadId;

    @Column(length = 20)
    private String resultado = "EXITOSO";

    @Column(length = 10)
    private String severidad = "INFO";

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "metodo_http", length = 10)
    private String metodoHttp;

    @Column(name = "ruta", length = 255)
    private String ruta;

    @Column(name = "estado_anterior", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String estadoNuevo;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
    }

    // Constructor de conveniencia
    public Auditoria(UUID usuarioId, String usuarioNombre,
                     String accion, String modulo,
                     UUID entidadId, String detalle) {
        this.usuarioId     = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.accion        = accion;
        this.modulo        = modulo;
        this.entidadId     = entidadId;
        this.detalle       = detalle;
    }
}
