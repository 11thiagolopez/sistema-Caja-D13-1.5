package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * total = paraTecnicos + paraNegocio. El técnico solo cobra sobre la mano de obra, así que paraNegocio
 * incluye los artículos; gananciaNegocio además les resta su costo.
 *
 * <p>Tiempos (Etapa 3): minutos desde el primer "En camino" hasta el aviso "Terminé" del técnico (o
 * el Terminado del jefe si el técnico no avisó), promediados sobre los trabajos que tienen los dos
 * registros en el historial; null si ninguno los tiene.
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
    Long tiempoPromedioMinutos,
    int trabajosConTiempo,
    List<PorTecnico> porTecnico,
    List<PuntoGraficoDTO> porDia,
    List<PuntoGraficoDTO> porMedioPago,
    List<PorMes> porMes,
    List<ComisionMes> comisionesPorMes,
    List<Trabajo> trabajos) {

    // idEmpleado null = trabajos sin técnico asignado.
    public record PorTecnico(Integer idEmpleado, String nombre, int cantidadTrabajos, BigDecimal manoDeObra,
        BigDecimal paraTecnico, BigDecimal paraNegocio, Long tiempoPromedioMinutos) {
    }

    /** mes: "2026-11". */
    public record PorMes(String mes, int cantidadTrabajos, BigDecimal total, BigDecimal paraTecnicos) {
    }

    /** Resumen para liquidar: lo que se llevó cada técnico en cada mes (por fecha de cobro). */
    public record ComisionMes(String mes, Integer idEmpleado, String nombre, int cantidadTrabajos,
        BigDecimal manoDeObra, BigDecimal paraTecnico) {
    }

    public record Trabajo(Integer idVenta, LocalDateTime fecha, String direccion, String cliente, String tecnico,
        String medioPago, BigDecimal total, BigDecimal manoDeObra, BigDecimal articulos, BigDecimal paraTecnico,
        BigDecimal paraNegocio, Long minutos) {
    }
}
