package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.ComisionEmpleadoDTO;
import com.thiago.escenasFX.dto.FormaPagoResumenDTO;
import com.thiago.escenasFX.dto.MarcaRankingDTO;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.GastoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private VentaRepository ventaRepo;

    @Mock
    private GastoRepository gastoRepo;

    @InjectMocks
    private ReporteService reporteService;

    private static Producto producto(Integer id, String marca) {
        Producto p = new Producto();
        p.setIdProducto(id);
        p.setDescripcion("Producto " + id);
        p.setMarca(marca);
        return p;
    }

    private static DetalleVenta detalle(Producto producto, int cantidad, String precioUnitario) {
        DetalleVenta d = new DetalleVenta();
        d.setProducto(producto);
        d.setCantidad(cantidad);
        BigDecimal precio = new BigDecimal(precioUnitario);
        d.setPrecioUnitario(precio);
        d.setSubtotal(precio.multiply(BigDecimal.valueOf(cantidad)));
        return d;
    }

    private static DetalleVenta detalleManual(String descripcion, int cantidad, String precioUnitario) {
        DetalleVenta d = new DetalleVenta();
        d.setDescripcion(descripcion);
        d.setCantidad(cantidad);
        BigDecimal precio = new BigDecimal(precioUnitario);
        d.setPrecioUnitario(precio);
        d.setSubtotal(precio.multiply(BigDecimal.valueOf(cantidad)));
        return d;
    }

    private static Venta venta(String medioPago, DetalleVenta... detalles) {
        Venta v = new Venta();
        v.setMedioPago(medioPago);
        v.getDetalles().addAll(List.of(detalles));
        BigDecimal total = List.of(detalles).stream().map(DetalleVenta::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        v.setTotalVenta(total);
        return v;
    }

    @Test
    void ventasPorMarca_agrupaPorMarcaYSumaCantidadYTotal() {
        Producto kalop = producto(1, "KALOP");
        Producto acytra = producto(2, "ACYTRA");
        Venta v1 = venta("EFECTIVO", detalle(kalop, 2, "100"), detalle(acytra, 1, "50"));
        Venta v2 = venta("TARJETA", detalle(kalop, 3, "100"));
        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), eq("CONFIRMADA")))
            .thenReturn(List.of(v1, v2));

        List<MarcaRankingDTO> resultado = reporteService.ventasPorMarca(LocalDate.now(), LocalDate.now());

        MarcaRankingDTO kalopRanking = resultado.stream().filter(r -> r.getMarca().equals("KALOP")).findFirst()
            .orElseThrow();
        assertThat(kalopRanking.getCantidadVendida()).isEqualTo(5);
        assertThat(kalopRanking.getTotalFacturado()).isEqualByComparingTo("500");

        MarcaRankingDTO acytraRanking = resultado.stream().filter(r -> r.getMarca().equals("ACYTRA")).findFirst()
            .orElseThrow();
        assertThat(acytraRanking.getCantidadVendida()).isEqualTo(1);
    }

    @Test
    void ventasPorMarca_productoSinMarca_seAgrupaComoSinMarca() {
        Producto sinMarca = producto(3, null);
        Venta v = venta("EFECTIVO", detalle(sinMarca, 1, "10"));
        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), anyString())).thenReturn(List.of(v));

        List<MarcaRankingDTO> resultado = reporteService.ventasPorMarca(LocalDate.now(), LocalDate.now());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMarca()).isEqualTo("Sin marca");
    }

    @Test
    void ventasPorFormaPago_sumaTotalesPorMedioPago() {
        Producto p = producto(1, "KALOP");
        Venta efectivo1 = venta("EFECTIVO", detalle(p, 1, "100"));
        Venta efectivo2 = venta("EFECTIVO", detalle(p, 1, "50"));
        Venta tarjeta = venta("TARJETA", detalle(p, 1, "200"));
        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), anyString()))
            .thenReturn(List.of(efectivo1, efectivo2, tarjeta));

        List<FormaPagoResumenDTO> resultado = reporteService.ventasPorFormaPago(LocalDate.now(), LocalDate.now());

        FormaPagoResumenDTO efectivo = resultado.stream().filter(r -> r.getMedioPago().equals("EFECTIVO"))
            .findFirst().orElseThrow();
        assertThat(efectivo.getCantidadVentas()).isEqualTo(2);
        assertThat(efectivo.getTotalFacturado()).isEqualByComparingTo("150");

        FormaPagoResumenDTO tarjetaResumen = resultado.stream().filter(r -> r.getMedioPago().equals("TARJETA"))
            .findFirst().orElseThrow();
        assertThat(tarjetaResumen.getCantidadVentas()).isEqualTo(1);
        assertThat(tarjetaResumen.getTotalFacturado()).isEqualByComparingTo("200");
    }

    @Test
    void productosGanadores_ignoraItemsManualesSinProducto() {
        Producto p = producto(1, "KALOP");
        Venta v = venta("EFECTIVO", detalle(p, 2, "100"), detalleManual("Apertura de cerradura", 1, "5000"));
        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), anyString())).thenReturn(List.of(v));

        var resultado = reporteService.productosGanadores(LocalDate.now(), LocalDate.now(), 10);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getIdProducto()).isEqualTo(1);
    }

    @Test
    void ventasPorMarca_ignoraItemsManualesSinProducto() {
        Producto p = producto(1, "KALOP");
        Venta v = venta("EFECTIVO", detalle(p, 1, "100"), detalleManual("Apertura de cerradura", 1, "5000"));
        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), anyString())).thenReturn(List.of(v));

        List<MarcaRankingDTO> resultado = reporteService.ventasPorMarca(LocalDate.now(), LocalDate.now());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMarca()).isEqualTo("KALOP");
    }

    @Test
    void balanceFinanciero_itemManualNoAportaCostoDeMercaderia() {
        Producto p = producto(1, "KALOP");
        p.setPrecioCompra(new BigDecimal("40"));
        Venta v = venta("EFECTIVO", detalle(p, 1, "100"), detalleManual("Apertura de cerradura", 1, "5000"));
        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), eq("CONFIRMADA"))).thenReturn(List.of(v));
        when(gastoRepo.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());

        var balance = reporteService.balanceFinanciero(LocalDate.now(), LocalDate.now());

        // Costo de mercadería: solo los $40 del artículo con producto real, el trabajo manual
        // ($5000 de ingreso) no suma costo.
        assertThat(balance.getCostoMercaderia()).isEqualByComparingTo("40");
        assertThat(balance.getIngresosPorVentas()).isEqualByComparingTo("5100");
    }

    @Test
    void comisionesPorVendedor_manoDeObraSeAtribuyeAlTecnicoNoAQuienCobra() {
        Empleado vendedor = new Empleado();
        vendedor.setIdEmpleado(1);
        vendedor.setNombre("Vendedor");
        vendedor.setComision(new BigDecimal("10"));

        Empleado tecnico = new Empleado();
        tecnico.setIdEmpleado(2);
        tecnico.setNombre("Técnico");
        tecnico.setComision(new BigDecimal("20"));

        Producto p = producto(1, "KALOP");
        p.setPrecioCompra(new BigDecimal("60"));

        DetalleVenta articulo = detalle(p, 1, "100"); // margen: 100 - 60 = 40
        DetalleVenta servicio = detalleManual("Instalación", 1, "3000");
        servicio.setTipo("SERVICIO");

        Venta v = venta("EFECTIVO", articulo, servicio);
        v.setEmpleado(vendedor);
        v.setTipoVenta("DOMICILIO");
        v.setEmpleadoTecnico(tecnico);

        when(ventaRepo.findByFechaBetweenAndEstado(any(), any(), eq("CONFIRMADA"))).thenReturn(List.of(v));

        List<ComisionEmpleadoDTO> resultado = reporteService.comisionesPorVendedor(LocalDate.now(), LocalDate.now());

        ComisionEmpleadoDTO comisionVendedor = resultado.stream()
            .filter(c -> c.getIdEmpleado().equals(1)).findFirst().orElseThrow();
        assertThat(comisionVendedor.getGananciaGenerada()).isEqualByComparingTo("40");
        assertThat(comisionVendedor.getComisionCalculada()).isEqualByComparingTo("4");

        ComisionEmpleadoDTO comisionTecnico = resultado.stream()
            .filter(c -> c.getIdEmpleado().equals(2)).findFirst().orElseThrow();
        // Monto bruto de la mano de obra (sin restar costo), no margen.
        assertThat(comisionTecnico.getGananciaGenerada()).isEqualByComparingTo("3000");
        assertThat(comisionTecnico.getComisionCalculada()).isEqualByComparingTo("600");
    }
}
