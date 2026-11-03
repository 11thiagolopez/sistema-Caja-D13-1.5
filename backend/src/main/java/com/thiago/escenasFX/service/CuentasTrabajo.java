package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Venta;

/**
 * El reparto de un trabajo a domicilio, único para que Comisiones, Balance y el reporte de trabajos
 * den lo mismo. El técnico cobra un % solo de la mano de obra; los artículos y el resto quedan para
 * el negocio.
 */
final class CuentasTrabajo {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CuentasTrabajo() {
    }

    static BigDecimal manoDeObra(Venta trabajo) {
        return trabajo.getDetalles().stream()
            .filter(d -> "SERVICIO".equals(d.getTipo()))
            .map(d -> d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Con el % guardado al cobrar, para que cambiarle la comisión a un técnico no altere lo que ya
     * se le liquidó. Los trabajos cobrados antes de guardarlo usan el % actual.
     */
    static BigDecimal paraTecnico(Venta trabajo) {
        if (trabajo.getEmpleadoTecnico() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal porcentaje = trabajo.getComisionTecnico() != null
            ? trabajo.getComisionTecnico()
            : trabajo.getEmpleadoTecnico().getComision();
        if (porcentaje == null) {
            return BigDecimal.ZERO;
        }
        return manoDeObra(trabajo).multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal costoArticulos(Venta trabajo) {
        BigDecimal costo = BigDecimal.ZERO;
        for (DetalleVenta d : trabajo.getDetalles()) {
            if (d.getProducto() != null && d.getProducto().getPrecioCompra() != null) {
                costo = costo.add(d.getProducto().getPrecioCompra().multiply(BigDecimal.valueOf(d.getCantidad())));
            }
        }
        return costo;
    }
}
