package com.thiago.escenasFX.controller;

import java.util.List;

import com.thiago.escenasFX.dto.CompraItemResponse;
import com.thiago.escenasFX.dto.CompraResponse;
import com.thiago.escenasFX.model.Compra;
import com.thiago.escenasFX.model.CompraItem;

/**
 * Igual que VentaMapper: evita serializar las entidades JPA directamente (Compra.items <->
 * CompraItem.compra es un ciclo bidireccional).
 */
final class CompraMapper {

    private CompraMapper() {
    }

    static CompraResponse toResponse(Compra compra) {
        List<CompraItemResponse> items = compra.getItems().stream()
            .map(CompraMapper::toResponse)
            .toList();

        return new CompraResponse(
            compra.getIdCompra(),
            compra.getFecha(),
            compra.getProveedor() != null ? compra.getProveedor().getIdProveedor() : null,
            compra.getProveedor() != null ? compra.getProveedor().getNombre() : null,
            compra.getMedioPago(),
            compra.getTotalCompra(),
            items);
    }

    private static CompraItemResponse toResponse(CompraItem i) {
        return new CompraItemResponse(
            i.getIdItem(),
            i.getProducto().getIdProducto(),
            i.getProducto().getDescripcion(),
            i.getProducto().getMarca(),
            i.getCantidad(),
            i.getPrecioCompraUnitario(),
            i.getPrecioVentaUnitario(),
            i.getSubtotal());
    }
}
