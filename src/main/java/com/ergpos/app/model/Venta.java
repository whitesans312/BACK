package com.ergpos.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Entity
@Table(name = "ventas")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Column(name = "cliente_nombre", length = 120)
    private String clienteNombre;

    @Column(name = "cliente_telefono", length = 20)
    private String clienteTelefono;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    @Column(nullable = false, length = 20)
    private String estado = "COMPLETADA";

    @Column(precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(updatable = false)
    private LocalDateTime fecha;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL,
               fetch = FetchType.EAGER, orphanRemoval = true)
    private List<VentaItem> items = new ArrayList<>();

    // ── Métodos de negocio ─────────────────────────────

    public void calcularTotal() {
        this.total = items.stream()
                .map(VentaItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addItem(VentaItem item) {
        items.add(item);
        item.setVenta(this);
    }

    public void removeItem(VentaItem item) {
        items.remove(item);
        item.setVenta(null);
    }

    // ── Hooks ─────────────────────────────────────────

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
        if (this.clienteNombre != null) {
            this.clienteNombre = this.clienteNombre.trim();
        }
    }
}