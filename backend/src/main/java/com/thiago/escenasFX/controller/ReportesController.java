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
import com.thiago.escenasFX.dto.ProductoRankingDTO;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.service.ReporteService;

/**
 * Protegido exclusivamente para ADMIN via SecurityConfig (matcher "/api/reportes/**").
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private final ReporteService reporteService;

    public ReportesController(ReporteService reporteService) {
        this.reporteService = reporteService;
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
}
