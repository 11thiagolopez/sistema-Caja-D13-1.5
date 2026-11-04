package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClienteResponse {
    private Integer idCliente;
    private String nombre;
    private Integer docTipo;
    private String docNro;
    private Integer condicionIva;
    private String telefono;
    private String email;
    private String direccion;
    private boolean activo;
    // Positivo = a favor, negativo = debe. Null en la búsqueda de Cobros: el vendedor solo ve el saldo
    // del cliente que eligió (GET /api/clientes/{id}/saldo).
    private BigDecimal saldo;
}
