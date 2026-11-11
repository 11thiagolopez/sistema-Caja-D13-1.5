package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Venta;

/**
 * Renglones de ticket y factura. El recargo de tarjeta no se muestra aparte (pedido del dueño,
 * 2026-11-11): se reparte entre los renglones en proporción a su precio, así los precios impresos ya
 * lo incluyen y la suma da el total cobrado. Los datos guardados (DetalleVenta) no se tocan: caja y
 * reportes siguen con el precio original.
 */
public final class LineasComprobante {

    public record Linea(String descripcion, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    private LineasComprobante() {
    }

    public static List<Linea> de(Venta venta) {
        List<DetalleVenta> detalles = venta.getDetalles();
        BigDecimal recargo = venta.getRecargoTarjeta() != null ? venta.getRecargoTarjeta() : BigDecimal.ZERO;
        BigDecimal bruto = detalles.stream().map(DetalleVenta::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> precios = new ArrayList<>();
        BigDecimal repartido = BigDecimal.ZERO;
        for (DetalleVenta d : detalles) {
            BigDecimal precio = d.getPrecioUnitario();
            if (recargo.signum() > 0 && bruto.signum() > 0) {
                // Por unidad y en centavos enteros, para que cantidad x precio siga dando el subtotal.
                BigDecimal extraUnitario = recargo.multiply(precio).divide(bruto, 2, RoundingMode.DOWN);
                precio = precio.add(extraUnitario);
                repartido = repartido.add(extraUnitario.multiply(BigDecimal.valueOf(d.getCantidad())));
            }
            precios.add(precio);
        }

        // Lo que quedó sin repartir por redondeo (centavos) va a un renglón de cantidad 1, o al último.
        BigDecimal resto = recargo.subtract(repartido);
        int destino = -1;
        if (resto.signum() > 0 && !detalles.isEmpty()) {
            destino = detalles.size() - 1;
            for (int i = 0; i < detalles.size(); i++) {
                if (detalles.get(i).getCantidad() == 1) {
                    destino = i;
                    break;
                }
            }
        }

        List<Linea> lineas = new ArrayList<>();
        for (int i = 0; i < detalles.size(); i++) {
            DetalleVenta d = detalles.get(i);
            BigDecimal cantidad = BigDecimal.valueOf(d.getCantidad());
            BigDecimal subtotal = precios.get(i).multiply(cantidad);
            BigDecimal precio = precios.get(i);
            if (i == destino) {
                subtotal = subtotal.add(resto);
                precio = subtotal.divide(cantidad, 2, RoundingMode.HALF_UP);
            }
            // La guardada al vender; si falta (filas viejas), la del producto. La mano de obra no tiene producto.
            String descripcion = d.getDescripcion() != null || d.getProducto() == null
                ? d.getDescripcion()
                : d.getProducto().getDescripcion();
            lineas.add(new Linea(descripcion, d.getCantidad(), precio, subtotal));
        }
        return lineas;
    }
}
