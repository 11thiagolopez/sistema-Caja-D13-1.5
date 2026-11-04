package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturarVentaRequest {

    // Cliente del catálogo: si viene, sus datos reemplazan a los de abajo y queda asignado a la venta.
    private Integer idCliente;

    // 80 = CUIT | 96 = DNI | 99 = Consumidor Final (en ese caso clienteDocNro va vacío). Obligatorio
    // si no viene idCliente.
    private Integer clienteDocTipo;

    // Solo dígitos: es justo lo que espera AFIP en DocNro, y evita mandar texto libre sin escapar
    // dentro del sobre SOAP armado a mano en AfipFacturacionService.
    @Pattern(regexp = "\\d*", message = "El número de documento debe contener solo dígitos")
    private String clienteDocNro;

    // Nombre/razón social del cliente: WSFEv1 no lo pide (FECAESolicitar solo lleva
    // DocTipo/DocNro), pero toda factura real de ARCA lo muestra impreso — FacturaFiscalService
    // exige este dato igual que clienteDocNro cuando clienteDocTipo no es Consumidor Final (99).
    private String clienteNombre;

    // CondicionIVAReceptorId de ARCA. Obligatorio con CUIT; con DNI o Consumidor Final se asume 5
    // (Consumidor Final) si no viene.
    private Integer condicionIvaReceptor;
}
