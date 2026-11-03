package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.BalanceFinancieroResponse;
import com.thiago.escenasFX.dto.ComisionEmpleadoDTO;
import com.thiago.escenasFX.dto.FormaPagoResumenDTO;
import com.thiago.escenasFX.dto.MarcaRankingDTO;
import com.thiago.escenasFX.dto.ProductoRankingDTO;
import com.thiago.escenasFX.dto.PuntoGraficoDTO;
import com.thiago.escenasFX.dto.RankingItemDTO;
import com.thiago.escenasFX.dto.RankingMarcasResponse;
import com.thiago.escenasFX.dto.ReporteTrabajosResponse;
import com.thiago.escenasFX.dto.ResumenVentasDTO;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.dto.VentasPorDiaResponse;
import com.thiago.escenasFX.service.ReporteGraficosService;
import com.thiago.escenasFX.service.ReporteService;
import com.thiago.escenasFX.service.ReporteTrabajosService;

/**
 * Protegido exclusivamente para ADMIN via SecurityConfig (matcher "/api/reportes/**").
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private final ReporteService reporteService;
    private final ReporteGraficosService reporteGraficosService;
    private final ReporteTrabajosService reporteTrabajosService;

    public ReportesController(ReporteService reporteService, ReporteGraficosService reporteGraficosService,
            ReporteTrabajosService reporteTrabajosService) {
        this.reporteService = reporteService;
        this.reporteGraficosService = reporteGraficosService;
        this.reporteTrabajosService = reporteTrabajosService;
    }

    @GetMapping("/trabajos-domicilio")
    public ReporteTrabajosResponse trabajosDomicilio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteTrabajosService.reporte(desde, hasta);
    }

    @GetMapping("/productos-ganadores")
    public List<ProductoRankingDTO> productosGanadores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {
        return reporteService.productosGanadores(desde, hasta, limit);
    }

    @GetMapping("/balance")
    public BalanceFinancieroResponse balance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.balanceFinanciero(desde, hasta);
    }

    @GetMapping("/comisiones")
    public List<ComisionEmpleadoDTO> comisiones(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.comisionesPorVendedor(desde, hasta);
    }

    @GetMapping("/ventas-por-vendedor")
    public List<VentaResponse> ventasPorVendedor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam Integer idEmpleado) {
        return reporteService.ventasPorVendedor(desde, hasta, idEmpleado).stream()
            .map(VentaMapper::toResponse)
            .toList();
    }

    @GetMapping("/ventas-por-marca")
    public List<MarcaRankingDTO> ventasPorMarca(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.ventasPorMarca(desde, hasta);
    }

    @GetMapping("/ventas-por-forma-pago")
    public List<FormaPagoResumenDTO> ventasPorFormaPago(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.ventasPorFormaPago(desde, hasta);
    }

    @GetMapping("/resumen")
    public ResumenVentasDTO resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteGraficosService.resumen(desde, hasta);
    }

    @GetMapping("/marcas")
    public RankingMarcasResponse marcas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "monto") String orden) {
        return reporteGraficosService.marcas(desde, hasta, orden);
    }

    @GetMapping("/productos")
    public List<RankingItemDTO> productos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "monto") String orden) {
        return reporteGraficosService.productos(desde, hasta, orden);
    }

    @GetMapping("/ventas-por-dia")
    public VentasPorDiaResponse ventasPorDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteGraficosService.ventasPorDia(desde, hasta);
    }

    @GetMapping("/ventas-por-dia-semana")
    public List<PuntoGraficoDTO> ventasPorDiaSemana(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteGraficosService.ventasPorDiaSemana(desde, hasta);
    }

    @GetMapping("/medios-pago")
    public List<PuntoGraficoDTO> mediosPago(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteGraficosService.mediosPago(desde, hasta);
    }
}
