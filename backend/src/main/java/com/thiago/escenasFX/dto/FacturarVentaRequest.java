package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturarVentaRequest {

    // 80 = CUIT | 96 = DNI | 99 = Consumidor Final (en ese caso clienteDocNro va vacío).
    @NotNull
    private Integer clienteDocTipo;

    private String clienteDocNro;
}
