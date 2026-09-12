package com.thiago.escenasFX.controller;

import com.thiago.escenasFX.dto.ProductoResponse;
import com.thiago.escenasFX.model.Producto;

/**
 * Igual que VentaMapper/CompraMapper/PresupuestoMapper: evita serializar la entidad JPA
 * directamente. Antes ProductoController devolvía Producto tal cual, lo que además forzaba
 * Hibernate a resolver proveedorRef (@ManyToOne) en cada fila listada aunque el frontend nunca lo
 * usa (solo lee el campo de texto legado "proveedor") — proveedorRef ahora es LAZY y este mapper
 * ni lo toca.
 */
final class ProductoMapper {

    private ProductoMapper() {
    }

    static ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
            p.getIdProducto(),
            p.getRubro(),
            p.getFamilia(),
            p.getMarca(),
            p.getNumeroMarca(),
            p.getCorrelativo(),
            p.getCodigoInterno(),
            p.getProveedor(),
            p.getCodigoFabrica(),
            p.getCodigoBarras(),
            p.getDescripcion(),
            p.getPrecioVenta(),
            p.getPrecioCompra(),
            p.getPrecioVentaUsd(),
            p.getPrecioCompraUsd(),
            p.getStockActual(),
            p.isActivo());
    }
}
