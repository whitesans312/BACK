package com.ergpos.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Entity
@Table(name = "devoluciones_garantias")
public class DevolucionGarantia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String tipo = "DEVOLUCION";

    @Column(nullable = false, length = 20)
    private String estado = "REGISTRADA";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entrega_id")
    private Entrega entrega;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "cliente_nombre", length = 120)
    private String clienteNombre;

    @NotBlank(message = "La razon es obligatoria")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String razon;

    @Column(name = "accion_dinero", nullable = false, length = 20)
    private String accionDinero = "REEMBOLSO";

    @Column(name = "monto_devuelto", precision = 10, scale = 2)
    private BigDecimal montoDevuelto = BigDecimal.ZERO;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonManagedReference
    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DevolucionGarantiaItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        normalizar();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        normalizar();
    }

    private void normalizar() {
        if (tipo != null) tipo = tipo.trim().toUpperCase();
        if (estado != null) estado = estado.trim().toUpperCase();
        if (accionDinero != null) accionDinero = accionDinero.trim().toUpperCase();
        if (razon != null) razon = razon.trim();
    }
}
