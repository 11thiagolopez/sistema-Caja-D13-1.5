package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.RecargoMedioPago;
import com.thiago.escenasFX.repository.RecargoMedioPagoRepository;

/**
 * Recargo por pagar con tarjeta en Cobros. El posnet se queda con un % de lo que paga el cliente,
 * así que se le cobra precio / (1 - comisión): con 20%, $100 se cobran $125 y el posnet se lleva
 * $25. La venta queda por $100 en caja, reportes y ganancias; el recargo solo va al ticket y a la
 * factura, que salen por lo que pagó el cliente.
 */
@Service
public class RecargoService {

    public static final String TARJETA_CREDITO = "TARJETA_CREDITO";
    public static final String TARJETA_DEBITO = "TARJETA_DEBITO";

    // Orden de la pantalla de configuración. Valores pedidos por el dueño (2026-11-11).
    private static final Map<String, BigDecimal> POR_DEFECTO = new LinkedHashMap<>();
    static {
        POR_DEFECTO.put(TARJETA_CREDITO, new BigDecimal("20.00"));
        POR_DEFECTO.put(TARJETA_DEBITO, new BigDecimal("15.00"));
    }

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final RecargoMedioPagoRepository repo;

    public RecargoService(RecargoMedioPagoRepository repo) {
        this.repo = repo;
    }

    public static boolean llevaRecargo(String medioPago) {
        return POR_DEFECTO.containsKey(medioPago);
    }

    public BigDecimal porcentajeDe(String medioPago) {
        if (!llevaRecargo(medioPago)) {
            return BigDecimal.ZERO;
        }
        return repo.findById(medioPago).map(RecargoMedioPago::getPorcentaje).orElse(POR_DEFECTO.get(medioPago));
    }

    public List<RecargoMedioPago> listar() {
        return POR_DEFECTO.keySet().stream().map(medio -> {
            RecargoMedioPago r = new RecargoMedioPago();
            r.setMedioPago(medio);
            r.setPorcentaje(porcentajeDe(medio));
            return r;
        }).toList();
    }

    @Transactional
    public RecargoMedioPago actualizar(String medioPago, BigDecimal porcentaje) {
        if (!llevaRecargo(medioPago)) {
            throw new IllegalArgumentException("El medio de pago " + medioPago + " no lleva recargo");
        }
        // 100% o más no tiene sentido: el posnet se quedaría con todo y la división explota.
        if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("El porcentaje tiene que estar entre 0 y 90");
        }
        RecargoMedioPago r = repo.findById(medioPago).orElseGet(RecargoMedioPago::new);
        r.setMedioPago(medioPago);
        r.setPorcentaje(porcentaje.setScale(2, RoundingMode.HALF_UP));
        return repo.save(r);
    }

    /**
     * Lo que hay que sumarle a {@code neto} para que, después de que el posnet se quede con
     * {@code porcentaje}% de lo cobrado, queden exactamente {@code neto} pesos. Redondea para arriba
     * al centavo: nunca se pierde plata por redondeo.
     */
    public static BigDecimal calcularRecargo(BigDecimal neto, BigDecimal porcentaje) {
        if (neto == null || neto.signum() <= 0 || porcentaje == null || porcentaje.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal factorQueQueda = BigDecimal.ONE.subtract(porcentaje.divide(CIEN, 6, RoundingMode.HALF_UP));
        BigDecimal cobrado = neto.divide(factorQueQueda, 2, RoundingMode.UP);
        return cobrado.subtract(neto);
    }
}
