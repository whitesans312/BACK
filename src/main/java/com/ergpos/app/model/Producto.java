package com.ergpos.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "El código es obligatorio")
    @Column(unique = true, length = 30)
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(length = 120)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    // Relación con categoría
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal precio;

    @Min(value = 0, message = "El stock no puede ser negativo")
    @Column(nullable = false)
    private Integer stock = 0;

    @Column(name = "stock_minimo")
    private Integer stockMinimo = 5;

    private Boolean activo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Lógica de negocio ─────────────────────────────

    public boolean tieneStock(int cantidad) {
        return this.stock != null && this.stock >= cantidad;
    }

    public void reducirStock(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad inválida para reducir stock");
        }

        if (!tieneStock(cantidad)) {
            throw new IllegalStateException(
                "Stock insuficiente para el producto: " + this.nombre
            );
        }

        this.stock -= cantidad;
    }

    public void aumentarStock(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad inválida para aumentar stock");
        }

        if (this.stock == null) {
            this.stock = 0;
        }

        this.stock += cantidad;
    }

    public boolean isStockCritico() {
        return this.stock != null && this.stock <= 0;
    }

    public boolean isStockBajo() {
        return this.stock != null && this.stockMinimo != null
                && this.stock <= this.stockMinimo;
    }

    public BigDecimal getValorInventario() {
        if (this.precio == null || this.stock == null) {
            return BigDecimal.ZERO;
        }
        return this.precio.multiply(BigDecimal.valueOf(this.stock));
    }

    // ── Hooks ────────────────────────────────────────

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}