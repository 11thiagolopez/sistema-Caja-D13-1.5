package com.thiago.escenasFX.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * "detalles" solo es obligatorio (y se valida) cuando alcance="PARCIAL" — NotaCreditoService lo
 * chequea a mano, no con anotaciones condicionales. Con alcance="TOTAL" el backend expande
 * automáticamente a todas las líneas de la venta original por su cantidad remanente.
 */
@Getter
@Setter
public class NotaCreditoRequest {

    @NotNull
    private Integer idVentaOriginal;

    @NotNull
    private Integer idEmpleado;

    // Nullable: si la venta original ya tiene un cliente vinculado se hereda; si no, es
    // obligatorio (NotaCreditoService rechaza con 400 si ninguno de los dos está presente).
    private Integer idCliente;

    @NotBlank
    private String motivo; // DEVOLUCION_MERCADERIA | ERROR_TIPEO | DESCUENTO_POSTERIOR | ANULACION_TOTAL

    @NotBlank
    private String alcance; // TOTAL | PARCIAL

    @Valid
    private List<DetalleNotaCreditoRequest> detalles;
}
