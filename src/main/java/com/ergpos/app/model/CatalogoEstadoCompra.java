// CatalogoEstadoCompra.java
package com.ergpos.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data @Entity @Table(name = "catalogo_estados_compra")
public class CatalogoEstadoCompra {
    @Id @Column(length = 20)
    private String codigo;
    @Column(nullable = false, length = 80)
    private String descripcion;
    @Column(name = "orden_visual")
    private Short ordenVisual;
}