package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturarVentaRequest {

    // 80 = CUIT | 96 = DNI | 99 = Consumidor Final (en ese caso clienteDocNro va vacío).
    @NotNull
    private Integer clienteDocTipo;

    // Solo dígitos: es justo lo que espera AFIP en DocNro, y evita mandar texto libre sin escapar
    // dentro del sobre SOAP armado a mano en AfipFacturacionService.
    @Pattern(regexp = "\\d*", message = "El número de documento debe contener solo dígitos")
    private String clienteDocNro;
}
