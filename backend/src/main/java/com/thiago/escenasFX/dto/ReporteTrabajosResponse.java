package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * total = paraTecnicos + paraNegocio. El técnico solo cobra sobre la mano de obra, así que paraNegocio
 * incluye los artículos; gananciaNegocio además les resta su costo.
 */
public record ReporteTrabajosResponse(
    int cantidadTrabajos,
    BigDecimal total,
    BigDecimal manoDeObra,
    BigDecimal articulos,
    BigDecimal paraTecnicos,
    BigDecimal paraNegocio,
    BigDecimal costoArticulos,
    BigDecimal gananciaNegocio,
    List<PorTecnico> porTecnico,
    List<PuntoGraficoDTO> porDia,
    List<PuntoGraficoDTO> porMedioPago,
    List<Trabajo> trabajos) {

    // idEmpleado null = trabajos sin técnico asignado.
    public record PorTecnico(Integer idEmpleado, String nombre, int cantidadTrabajos, BigDecimal manoDeObra,
        BigDecimal paraTecnico, BigDecimal paraNegocio) {
    }

    public record Trabajo(Integer idVenta, LocalDateTime fecha, String direccion, String cliente, String tecnico,
        String medioPago, BigDecimal total, BigDecimal manoDeObra, BigDecimal articulos, BigDecimal paraTecnico,
        BigDecimal paraNegocio) {
    }
}
