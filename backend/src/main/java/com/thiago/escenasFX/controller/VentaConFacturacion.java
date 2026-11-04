package com.thiago.escenasFX.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;

/**
 * Arma la respuesta de una venta con su estado de facturación, que se deduce de facturas_fiscales
 * en vez de guardarse en ventas: dos datos separados podrían llegar a contradecirse. Trae todo en
 * una sola consulta, para que el historial no haga un pedido por venta.
 */
@Component
class VentaConFacturacion {

    private final FacturaFiscalRepository facturaRepo;
    private final NotaCreditoRepository notaCreditoRepo;

    VentaConFacturacion(FacturaFiscalRepository facturaRepo, NotaCreditoRepository notaCreditoRepo) {
        this.facturaRepo = facturaRepo;
        this.notaCreditoRepo = notaCreditoRepo;
    }

    VentaResponse responder(Venta venta) {
        return responder(List.of(venta)).get(0);
    }

    List<VentaResponse> responder(List<Venta> ventas) {
        if (ventas.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = ventas.stream().map(Venta::getIdVenta).toList();
        Map<Integer, FacturaFiscal> facturas = facturaRepo.findByVentaIdVentaIn(ids).stream()
            .collect(Collectors.toMap(f -> f.getVenta().getIdVenta(), Function.identity()));
        Set<Integer> conNota = Set.copyOf(notaCreditoRepo.idsDeVentasConNota(ids));

        return ventas.stream()
            .map(v -> VentaMapper.toResponse(v, facturas.get(v.getIdVenta()), conNota.contains(v.getIdVenta())))
            .toList();
    }
}
