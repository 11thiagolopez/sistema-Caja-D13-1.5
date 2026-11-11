package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Venta;

class LineasComprobanteTest {

    private DetalleVenta detalle(String descripcion, int cantidad, String precio) {
        DetalleVenta d = new DetalleVenta();
        d.setDescripcion(descripcion);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setSubtotal(new BigDecimal(precio).multiply(BigDecimal.valueOf(cantidad)));
        return d;
    }

    private Venta venta(String recargo, DetalleVenta... detalles) {
        Venta v = new Venta();
        v.setDetalles(new ArrayList<>(List.of(detalles)));
        v.setRecargoTarjeta(recargo != null ? new BigDecimal(recargo) : null);
        return v;
    }

    private BigDecimal suma(List<LineasComprobante.Linea> lineas) {
        return lineas.stream().map(LineasComprobante.Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void sinRecargo_quedanLosPreciosOriginales() {
        List<LineasComprobante.Linea> lineas = LineasComprobante.de(venta(null, detalle("Candado", 2, "8900.00")));

        assertThat(lineas).hasSize(1);
        assertThat(lineas.get(0).precioUnitario()).isEqualByComparingTo("8900.00");
        assertThat(lineas.get(0).subtotal()).isEqualByComparingTo("17800.00");
    }

    @Test
    void unSoloProducto_elPrecioYaIncluyeElRecargo_sinRenglonAparte() {
        List<LineasComprobante.Linea> lineas = LineasComprobante.de(venta("4625.00", detalle("Cerradura", 1, "18500.00")));

        assertThat(lineas).hasSize(1);
        assertThat(lineas.get(0).descripcion()).isEqualTo("Cerradura");
        assertThat(lineas.get(0).precioUnitario()).isEqualByComparingTo("23125.00");
        assertThat(lineas.get(0).subtotal()).isEqualByComparingTo("23125.00");
    }

    @Test
    void variosRenglones_sumanExactoElTotalCobrado_yCantidadPorPrecioCierra() {
        // 3 x 333.33 + 1 x 100.01 = 1100.00; débito 15% → cobrado 1294.12, recargo 194.12.
        Venta v = venta("194.12", detalle("Llave", 3, "333.33"), detalle("Copia", 1, "100.01"));

        List<LineasComprobante.Linea> lineas = LineasComprobante.de(v);

        assertThat(lineas).hasSize(2);
        assertThat(suma(lineas)).isEqualByComparingTo("1294.12");
        for (LineasComprobante.Linea l : lineas) {
            assertThat(l.precioUnitario().multiply(BigDecimal.valueOf(l.cantidad()))).isEqualByComparingTo(l.subtotal());
        }
    }
}
