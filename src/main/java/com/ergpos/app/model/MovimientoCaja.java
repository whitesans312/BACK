package com.ergpos.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    @NotBlank(message = "El tipo de movimiento es obligatorio")
    @Column(length = 20)
    private String tipo; // INGRESO, EGRESO, PAGO_VENTA, PAGO_ORDEN, DEVOLUCIÓN, COMPRA, AJUSTE

    @NotBlank(message = "El concepto es obligatorio")
    @Column(length = 255)
    private String concepto;

    @NotNull(message = "El monto es obligatorio")
    @Column(precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(length = 100)
    private String referencia; // ID de orden/venta/compra si aplica

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(updatable = false)
    private LocalDateTime fecha;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
}
