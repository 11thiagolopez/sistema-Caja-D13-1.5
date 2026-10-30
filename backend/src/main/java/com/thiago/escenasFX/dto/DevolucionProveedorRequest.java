package com.thiago.escenasFX.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DevolucionProveedorRequest {

    @NotNull
    private Integer idProveedor;

    @NotNull
    private Integer idEmpleado;

    // Opcional, puramente informativa — ver comentario en DevolucionProveedor.
    private Integer idCompraOriginal;

    private String motivo;

    @NotEmpty
    @Valid
    private List<DetalleDevolucionProveedorRequest> detalles;
}
