package com.thiago.escenasFX.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "marcas")
@Getter
@Setter
public class Marca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca")
    private Integer idMarca;

    private String nombre;

    // Código de 2 dígitos usado internamente para armar Producto.codigoInterno, igual que antes
    // de tener este catálogo (rubro+familia+marca+correlativo). Asignado automáticamente por
    // MarcaService, nunca lo tipea el usuario.
    private String codigo;

    private boolean activo = true;
}
