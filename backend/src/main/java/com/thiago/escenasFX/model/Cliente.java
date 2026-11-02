package com.thiago.escenasFX.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "clientes")
@Getter
@Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    private String nombre;

    // 80 CUIT | 96 DNI | 99 sin documento — misma convención que FacturaFiscal.clienteDocTipo.
    @Column(name = "doc_tipo", nullable = false)
    private Integer docTipo = 99;

    @Column(name = "doc_nro")
    private String docNro;

    // Condición frente al IVA, con los códigos de ARCA (CondicionIVAReceptorId). Null si no se sabe.
    @Column(name = "condicion_iva")
    private Integer condicionIva;

    private String telefono;
    private String email;
    private String direccion;

    // Baja lógica, mismo patrón que Proveedor.activo.
    private boolean activo = true;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
