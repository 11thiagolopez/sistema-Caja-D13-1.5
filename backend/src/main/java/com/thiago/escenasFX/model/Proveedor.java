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
@Table(name = "proveedores")
@Getter
@Setter
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Integer idProveedor;

    private String nombre;
    private String contacto;
    private String telefono;
    private String email;

    // Baja lógica, mismo patrón que Producto.activo: no se borra para no romper productos/compras
    // históricas que ya lo referencian.
    private boolean activo = true;
}
