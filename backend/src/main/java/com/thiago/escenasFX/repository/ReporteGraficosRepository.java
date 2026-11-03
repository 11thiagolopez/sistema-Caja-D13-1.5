package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.Venta;

/**
 * Agregaciones de la pantalla de gráficos de Reportes, resueltas en SQL: el período puede abarcar
 * miles de ventas y traerlas a Java solo para sumarlas no escala (ver ReporteGraficosService).
 *
 * Siempre se agrupa por expresiones sin parámetros ni literales: con un parámetro en el SELECT y
 * otro en el GROUP BY, Postgres los ve como expresiones distintas y rechaza la consulta.
 */
public interface ReporteGraficosRepository extends Repository<Venta, Integer> {

    // Línea neta de descuento: subtotal no lo descuenta y total_venta sí, así que se reparte el
    // descuento de la venta proporcionalmente entre sus líneas. El nullif evita dividir por cero
    // en una venta de total 0 (la línea queda en null y SUM la ignora).
    String MONTO_NETO = "coalesce(sum(d.subtotal * v.totalVenta / nullif(v.totalVenta + v.descuento, 0)), 0)";

    // Los trabajos a domicilio tienen su propio reporte (ReporteTrabajosService).
    String VENTAS_DEL_PERIODO = "v.estado = 'CONFIRMADA' and v.tipoVenta <> 'DOMICILIO'"
        + " and v.fecha >= :inicio and v.fecha < :fin";

    String DETALLES_DE_CATALOGO = "from DetalleVenta d join d.venta v join d.producto p where " + VENTAS_DEL_PERIODO;

    @Query("select coalesce(sum(v.totalVenta), 0), count(v) from Venta v where " + VENTAS_DEL_PERIODO)
    List<Object[]> totalesDeVentas(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("select coalesce(sum(d.cantidad), 0) from DetalleVenta d join d.venta v where d.tipo <> 'SERVICIO' and "
        + VENTAS_DEL_PERIODO)
    Number unidadesVendidas(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("select upper(trim(p.marca)), " + MONTO_NETO + ", sum(d.cantidad) " + DETALLES_DE_CATALOGO
        + " group by upper(trim(p.marca))")
    List<Object[]> totalesPorMarca(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("select p.descripcion, " + MONTO_NETO + " as monto, sum(d.cantidad) as unidades " + DETALLES_DE_CATALOGO
        + " group by p.idProducto, p.descripcion order by monto desc, unidades desc")
    List<Object[]> productosPorMonto(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin,
        Pageable pagina);

    @Query("select p.descripcion, " + MONTO_NETO + " as monto, sum(d.cantidad) as unidades " + DETALLES_DE_CATALOGO
        + " group by p.idProducto, p.descripcion order by unidades desc, monto desc")
    List<Object[]> productosPorUnidades(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin,
        Pageable pagina);

    @Query("select upper(trim(v.medioPago)), coalesce(sum(v.totalVenta), 0), coalesce(sum(v.montoSaldoAplicado), 0)"
        + " from Venta v where " + VENTAS_DEL_PERIODO + " group by upper(trim(v.medioPago))")
    List<Object[]> totalesPorMedioPago(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // Nativa porque HQL no tiene AT TIME ZONE. GROUP BY 1 evita repetir los parámetros de zona (ver
    // el comentario de la interfaz). El CAST a timestamp hace que la conversión funcione igual si
    // la columna real fuera timestamptz.
    @Query(value = """
        SELECT CAST(((CAST(v.fecha AS timestamp) AT TIME ZONE CAST(:zonaGuardada AS varchar))
                    AT TIME ZONE CAST(:zonaNegocio AS varchar)) AS date),
               SUM(v.total_venta)
        FROM ventas v
        WHERE v.estado = 'CONFIRMADA' AND v.tipo_venta <> 'DOMICILIO' AND v.fecha >= :inicio AND v.fecha < :fin
        GROUP BY 1
        """, nativeQuery = true)
    List<Object[]> totalesPorDia(@Param("zonaGuardada") String zonaGuardada, @Param("zonaNegocio") String zonaNegocio,
        @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
