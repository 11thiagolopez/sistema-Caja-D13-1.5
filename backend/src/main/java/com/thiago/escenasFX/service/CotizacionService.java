package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.exception.CotizacionNoDisponibleException;
import com.thiago.escenasFX.model.CotizacionDolar;
import com.thiago.escenasFX.repository.CotizacionDolarRepository;

@Service
public class CotizacionService {

    private final CotizacionDolarRepository cotizacionRepo;
    private final CotizacionApiClient apiClient;

    public CotizacionService(CotizacionDolarRepository cotizacionRepo, CotizacionApiClient apiClient) {
        this.cotizacionRepo = cotizacionRepo;
        this.apiClient = apiClient;
    }

    /**
     * Si ya se consultó (o cargó a mano) una cotización hoy, la reusa — no le pega de nuevo a las
     * APIs si ya se abrió/cerró caja hoy. Si no, prueba la fuente primaria y, si falla, la
     * secundaria. Si las dos fallan, tira CotizacionNoDisponibleException: nunca reusa en
     * silencio una cotización vieja.
     */
    public CotizacionDolar obtenerCotizacionDelDia() {
        Optional<CotizacionDolar> deHoy = cotizacionRepo.findFirstByFechaOrderByCreadoEnDesc(LocalDate.now());
        if (deHoy.isPresent()) {
            return deHoy.get();
        }

        Optional<BigDecimal> primaria = apiClient.consultarPrimaria();
        if (primaria.isPresent()) {
            return guardar(primaria.get(), "dolarapi.com", false);
        }

        Optional<BigDecimal> secundaria = apiClient.consultarSecundaria();
        if (secundaria.isPresent()) {
            return guardar(secundaria.get(), "dolar-bna", false);
        }

        throw new CotizacionNoDisponibleException(
            "No se pudo obtener la cotización del dólar oficial. Cargala a mano para continuar.");
    }

    public CotizacionDolar registrarManual(BigDecimal valorVenta) {
        return guardar(valorVenta, "MANUAL", true);
    }

    /**
     * Última cotización conocida, sin importar la fecha — usada como ancla en USD fuera del flujo
     * de apertura de caja (alta/edición de productos, compras).
     */
    public Optional<BigDecimal> ultimaConocida() {
        return cotizacionRepo.findFirstByOrderByCreadoEnDesc().map(CotizacionDolar::getValorVenta);
    }

    private CotizacionDolar guardar(BigDecimal valorVenta, String fuente, boolean manual) {
        CotizacionDolar cotizacion = new CotizacionDolar();
        cotizacion.setFecha(LocalDate.now());
        cotizacion.setValorVenta(valorVenta);
        cotizacion.setFuente(fuente);
        cotizacion.setManual(manual);
        return cotizacionRepo.save(cotizacion);
    }
}
