package com.thiago.escenasFX.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "productos")
@Getter
@Setter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    private String rubro;
    private String familia;

    // Código de 2 dígitos de Marca.codigo, usado para armar codigoInterno. No confundir con
    // `marca` (nombre para mostrar/buscar).
    @Column(name = "numero_marca")
    private String numeroMarca;

    // Nombre de la marca para mostrar y para buscar en Productos/Ventas/Compras (ej. "KALOP").
    // Sincronizado con Marca.nombre al crear el producto.
    private String marca;

    private String correlativo;

    @Column(name = "codigo_interno")
    private String codigoInterno;

    // Texto libre histórico, previo al catálogo de proveedores. Se sigue escribiendo por
    // compatibilidad pero ya no se usa para mostrar/filtrar — ver proveedorRef.
    private String proveedor;

    // LAZY a propósito: nada en el backend lee este campo hoy (solo se setea al crear el
    // producto) y el frontend usa el campo de texto legado "proveedor", no este FK — con EAGER,
    // Hibernate resolvía un SELECT extra por fila en cada listado de productos (~6900 filas) sin
    // que nadie usara el resultado.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proveedor")
    private Proveedor proveedorRef;

    @Column(name = "codigo_fabrica")
    private String codigoFabrica;

    // Valor único usado para el escaneo/impresión de etiqueta: codigoFabrica si el producto viene
    // con código de fábrica, o codigoInterno si no lo tiene (productos sueltos/cortados a medida,
    // sin envoltorio con EAN) — asignado en ProductoService, nunca se genera un código nuevo.
    // No reemplaza la búsqueda por barcode en Ventas (sigue resolviendo por codigoFabrica/
    // codigoInterno vía buscarActivoPorCodigo), solo sirve para imprimir la etiqueta física y
    // garantizar unicidad.
    @Column(name = "codigo_barras")
    private String codigoBarras;

    private String descripcion;

    @Column(name = "precio_venta", precision = 12, scale = 2)
    private BigDecimal precioVenta;

    // Costo de compra, usado para calcular "Costo de Mercadería" en el balance financiero.
    @Column(name = "precio_compra", precision = 12, scale = 2)
    private BigDecimal precioCompra;

    // Ancla en USD de precioVenta/precioCompra (dolarización): se recalcula sola cada vez que se
    // fija un precio en pesos (ver ProductoService.sincronizarAnclaUsd) y es lo que usa el
    // recálculo masivo al abrir caja para volver a pesos con la cotización del día. Uso interno —
    // nunca aparece en comprobantes de cara al cliente.
    @Column(name = "precio_venta_usd", precision = 12, scale = 2)
    private BigDecimal precioVentaUsd;

    @Column(name = "precio_compra_usd", precision = 12, scale = 2)
    private BigDecimal precioCompraUsd;

    @Column(name = "stock_actual")
    private int stockActual;

    // Baja lógica: un producto eliminado deja de listarse/venderse pero no rompe la integridad
    // de las ventas históricas que ya lo referencian (DetalleVenta -> Producto no tiene cascade).
    private boolean activo = true;
}
