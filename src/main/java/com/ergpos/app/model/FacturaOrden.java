package com.ergpos.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "facturas_orden")
public class FacturaOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación 1-a-1 con la orden — UNIQUE garantizado en BD
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entrega_id", nullable = false, unique = true)
    private Entrega entrega;

    // Lo genera la secuencia seq_facturas_orden en Supabase (FO-0001...)
    // insertable=false: Hibernate no lo manda en el INSERT, la BD lo pone sola
    @Column(nullable = false, unique = true, length = 20, insertable = false, updatable = false)
    private String numero;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal impuesto = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision = LocalDateTime.now();
}