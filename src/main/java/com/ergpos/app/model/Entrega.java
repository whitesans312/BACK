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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Entity
@Table(name = "entregas")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Cliente ──────────────────────────────────────────────
    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Column(name = "cliente_nombre", length = 120)
    private String clienteNombre;

    @Column(name = "cliente_telefono", length = 20)
    private String clienteTelefono;

    // FK opcional al cliente registrado
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // ── Tipo y descripción ───────────────────────────────────
    // referencia catalogo_tipos_entrega
    @Column(nullable = false, length = 20)
    private String tipo = "REPARACION";

    @Column(name = "descripcion_problema", columnDefinition = "TEXT")
    private String descripcionProblema;

    // ── Logística ────────────────────────────────────────────
    @NotBlank(message = "La dirección es obligatoria")
    @Column(length = 200)
    private String direccion;

    // ── Estado ───────────────────────────────────────────────
    // referencia catalogo_estados_orden
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    // ── Personal ─────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tecnico_id")
    private Usuario tecnico;

    // auditoría: usuario que creó la orden
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "notas_tecnico", length = 500)
    private String notasTecnico;

    // ── Fechas ───────────────────────────────────────────────
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    // ── Finanzas ─────────────────────────────────────────────
    @Column(name = "mano_obra", precision = 10, scale = 2)
    private BigDecimal manoObra = BigDecimal.ZERO;

    @Column(name = "total_orden", precision = 10, scale = 2)
    private BigDecimal totalOrden = BigDecimal.ZERO;

    @Column(name = "anticipo_porcentaje", precision = 5, scale = 2)
    private BigDecimal anticipoPorcentaje = BigDecimal.ZERO;

    @Column(name = "anticipo_recibido", precision = 10, scale = 2)
    private BigDecimal anticipoRecibido = BigDecimal.ZERO;

    // referencia catalogo_estados_pago
    @Column(name = "estado_pago", nullable = false, length = 20)
    private String estadoPago = "PENDIENTE";

    // ── Auditoría ─────────────────────────────────────────────
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Relaciones ────────────────────────────────────────────
    @JsonManagedReference
    @OneToMany(mappedBy = "entrega", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    private List<EntregaItem> items = new ArrayList<>();

    @JsonManagedReference("entrega-pagos")
    @OneToMany(mappedBy = "entrega", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PagoOrden> pagos = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}