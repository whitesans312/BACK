package com.ergpos.app.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "movimientos_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull(message = "El producto es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // ENTRADA o SALIDA — referencia catalogo_tipos_movimiento
    @NotBlank(message = "El tipo es obligatorio")
    @Column(length = 10)
    private String tipo;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;

    // ← antes era String proveedor
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 255)
    private String observacion;

    // Trazabilidad: qué operación originó este movimiento
    // Valores: VENTA, COMPRA, ENTREGA, DEVOLUCION, AJUSTE, MANUAL
    @Column(name = "origen_tipo", length = 20)
    private String origenTipo;

    // UUID del registro origen (ventas.id, compras.id, entregas.id)
    @Column(name = "origen_id")
    private UUID origenId;

    private LocalDateTime fecha = LocalDateTime.now();
}
