// CatalogoTipoEntrega.java
package com.ergpos.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data @Entity @Table(name = "catalogo_tipos_entrega")
public class CatalogoTipoEntrega {
    @Id @Column(length = 20)
    private String codigo;
    @Column(nullable = false, length = 80)
    private String descripcion;
}