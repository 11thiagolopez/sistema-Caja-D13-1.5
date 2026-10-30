package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.CompraItemRequest;
import com.thiago.escenasFX.dto.CompraRequest;
import com.thiago.escenasFX.dto.DetalleDevolucionProveedorRequest;
import com.thiago.escenasFX.dto.DevolucionProveedorRequest;
import com.thiago.escenasFX.model.Compra;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.repository.CompraRepository;
import com.thiago.escenasFX.repository.DevolucionProveedorRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteProveedorRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock
    private CompraRepository compraRepo;
    @Mock
    private ProductoRepository productoRepo;
    @Mock
    private ProductoService productoService;
    @Mock
    private ProveedorService proveedorService;
    @Mock
    private MovimientoCuentaCorrienteProveedorRepository movimientoProveedorRepo;
    @Mock
    private DevolucionProveedorRepository devolucionProveedorRepo;

    @InjectMocks
    private CompraService compraService;

    @BeforeEach
    void configuracionPorDefecto() {
        lenient().when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Proveedor proveedor(int id) {
        Proveedor p = new Proveedor();
        p.setIdProveedor(id);
        p.setNombre("Proveedor de prueba");
        return p;
    }

    private Producto producto(int id, int stockActual) {
        Producto p = new Producto();
        p.setIdProducto(id);
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockActual);
        return p;
    }

    @Test
    void registrarCompra_productoExistente_sincronizaAnclaUsdAntesDeGuardar() {
        Producto producto = new Producto();
        producto.setIdProducto(3);
        when(productoRepo.findById(3)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(proveedorService.resolverOCrear("Proveedor SA")).thenReturn(new Proveedor());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        CompraItemRequest item = new CompraItemRequest();
        item.setIdProducto(3);
        item.setCantidad(5);
        item.setPrecioCompraUnitario(new BigDecimal("100"));

        CompraRequest req = new CompraRequest();
        req.setIdEmpleado(1);
        req.setFecha(LocalDate.now());
        req.setProveedorNombre("Proveedor SA");
        req.setMedioPago("EFECTIVO");
        req.setItems(List.of(item));

        compraService.registrarCompra(req, new Empleado());

        verify(productoService).sincronizarAnclaUsd(producto);
        assertThat(producto.getStockActual()).isEqualTo(5);
        assertThat(producto.getPrecioCompra()).isEqualByComparingTo("100");
    }

    private CompraRequest compraReq(int idProducto, String precioCompra, BigDecimal montoPagado) {
        CompraItemRequest item = new CompraItemRequest();
        item.setIdProducto(idProducto);
        item.setCantidad(1);
        item.setPrecioCompraUnitario(new BigDecimal(precioCompra));

        CompraRequest req = new CompraRequest();
        req.setIdEmpleado(1);
        req.setFecha(LocalDate.now());
        req.setProveedorNombre("Proveedor de prueba");
        req.setMedioPago("EFECTIVO");
        req.setItems(List.of(item));
        req.setMontoPagado(montoPagado);
        return req;
    }

    @Test
    void registrarCompra_sinMontoPagado_pagaTodoYDejaSaldoPendienteEnCero() {
        Producto producto = producto(3, 0);
        when(productoRepo.findById(3)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        Proveedor proveedor = proveedor(1);
        when(proveedorService.resolverOCrear("Proveedor de prueba")).thenReturn(proveedor);

        Compra guardada = compraService.registrarCompra(compraReq(3, "100", null), new Empleado());

        assertThat(guardada.getMontoPagado()).isEqualByComparingTo("100");

        ArgumentCaptor<MovimientoCuentaCorrienteProveedor> captor =
            ArgumentCaptor.forClass(MovimientoCuentaCorrienteProveedor.class);
        verify(movimientoProveedorRepo, times(2)).save(captor.capture());
        List<MovimientoCuentaCorrienteProveedor> movimientos = captor.getAllValues();
        assertThat(movimientos.get(0).getTipo()).isEqualTo("DEBE_COMPRA");
        assertThat(movimientos.get(0).getMonto()).isEqualByComparingTo("100");
        assertThat(movimientos.get(1).getTipo()).isEqualTo("HABER_PAGO");
        assertThat(movimientos.get(1).getMonto()).isEqualByComparingTo("100");
    }

    @Test
    void registrarCompra_conMontoPagadoParcial_dejaDeudaPendiente() {
        Producto producto = producto(3, 0);
        when(productoRepo.findById(3)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(proveedorService.resolverOCrear("Proveedor de prueba")).thenReturn(proveedor(1));

        Compra guardada = compraService.registrarCompra(compraReq(3, "100", new BigDecimal("30")), new Empleado());

        assertThat(guardada.getTotalCompra()).isEqualByComparingTo("100");
        assertThat(guardada.getMontoPagado()).isEqualByComparingTo("30");
        // Solo un pago parcial: DEBE_COMPRA(100) + HABER_PAGO(30), ambos se guardan.
        verify(movimientoProveedorRepo, times(2)).save(any());
    }

    @Test
    void registrarCompra_montoPagadoMayorAlTotal_lanzaExcepcion() {
        Producto producto = producto(3, 0);
        when(productoRepo.findById(3)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(proveedorService.resolverOCrear("Proveedor de prueba")).thenReturn(proveedor(1));

        assertThatThrownBy(() -> compraService.registrarCompra(compraReq(3, "100", new BigDecimal("150")), new Empleado()))
            .isInstanceOf(IllegalArgumentException.class);

        verify(compraRepo, never()).save(any());
    }

    @Test
    void registrarPago_dentroDelSaldoPendiente_incrementaMontoPagado() {
        Compra compra = new Compra();
        compra.setIdCompra(10);
        compra.setProveedor(proveedor(1));
        compra.setTotalCompra(new BigDecimal("100"));
        compra.setMontoPagado(new BigDecimal("30"));
        when(compraRepo.findById(10)).thenReturn(Optional.of(compra));

        Compra guardada = compraService.registrarPago(10, new BigDecimal("50"), "EFECTIVO", new Empleado());

        assertThat(guardada.getMontoPagado()).isEqualByComparingTo("80");
        ArgumentCaptor<MovimientoCuentaCorrienteProveedor> captor =
            ArgumentCaptor.forClass(MovimientoCuentaCorrienteProveedor.class);
        verify(movimientoProveedorRepo).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("HABER_PAGO");
        assertThat(captor.getValue().getMonto()).isEqualByComparingTo("50");
    }

    @Test
    void registrarPago_superaElSaldoPendiente_lanzaExcepcion() {
        Compra compra = new Compra();
        compra.setIdCompra(10);
        compra.setProveedor(proveedor(1));
        compra.setTotalCompra(new BigDecimal("100"));
        compra.setMontoPagado(new BigDecimal("80"));
        when(compraRepo.findById(10)).thenReturn(Optional.of(compra));

        assertThatThrownBy(() -> compraService.registrarPago(10, new BigDecimal("50"), "EFECTIVO", new Empleado()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("supera el saldo pendiente");

        verify(movimientoProveedorRepo, never()).save(any());
    }

    @Test
    void registrarDevolucion_reduceStockYAcreditaLaCuentaCorrienteDelProveedor() {
        Proveedor proveedor = proveedor(1);
        when(proveedorService.obtenerPorId(1)).thenReturn(proveedor);
        Producto producto = producto(5, 10);
        when(productoRepo.buscarPorIdConLock(5)).thenReturn(Optional.of(producto));
        when(devolucionProveedorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DetalleDevolucionProveedorRequest detalle = new DetalleDevolucionProveedorRequest();
        detalle.setIdProducto(5);
        detalle.setCantidad(3);
        detalle.setPrecioUnitario(new BigDecimal("50"));

        DevolucionProveedorRequest req = new DevolucionProveedorRequest();
        req.setIdProveedor(1);
        req.setIdEmpleado(1);
        req.setMotivo("Mercadería defectuosa");
        req.setDetalles(List.of(detalle));

        var devolucion = compraService.registrarDevolucion(req, new Empleado());

        assertThat(producto.getStockActual()).isEqualTo(7); // 10 - 3
        assertThat(devolucion.getMontoTotal()).isEqualByComparingTo("150"); // 3 * 50

        ArgumentCaptor<MovimientoCuentaCorrienteProveedor> captor =
            ArgumentCaptor.forClass(MovimientoCuentaCorrienteProveedor.class);
        verify(movimientoProveedorRepo).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("HABER_DEVOLUCION");
        assertThat(captor.getValue().getMonto()).isEqualByComparingTo("150");
    }

    @Test
    void registrarDevolucion_stockInsuficiente_lanzaExcepcionYNoAcreditaNada() {
        when(proveedorService.obtenerPorId(1)).thenReturn(proveedor(1));
        Producto producto = producto(5, 2);
        when(productoRepo.buscarPorIdConLock(5)).thenReturn(Optional.of(producto));

        DetalleDevolucionProveedorRequest detalle = new DetalleDevolucionProveedorRequest();
        detalle.setIdProducto(5);
        detalle.setCantidad(3);
        detalle.setPrecioUnitario(new BigDecimal("50"));

        DevolucionProveedorRequest req = new DevolucionProveedorRequest();
        req.setIdProveedor(1);
        req.setIdEmpleado(1);
        req.setDetalles(List.of(detalle));

        assertThatThrownBy(() -> compraService.registrarDevolucion(req, new Empleado()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(producto.getStockActual()).isEqualTo(2); // sin cambios
        verify(movimientoProveedorRepo, never()).save(any());
        verify(devolucionProveedorRepo, never()).save(any());
    }

    @Test
    void calcularSaldoProveedor_sumaDebeYRestaHaberes() {
        when(movimientoProveedorRepo.findByProveedorIdProveedorOrderByFechaDesc(1)).thenReturn(List.of(
            movimiento("DEBE_COMPRA", "1000"),
            movimiento("HABER_PAGO", "300"),
            movimiento("HABER_DEVOLUCION", "200")));

        BigDecimal saldo = compraService.calcularSaldoProveedor(1);

        assertThat(saldo).isEqualByComparingTo("500"); // 1000 - 300 - 200
    }

    private MovimientoCuentaCorrienteProveedor movimiento(String tipo, String monto) {
        MovimientoCuentaCorrienteProveedor m = new MovimientoCuentaCorrienteProveedor();
        m.setTipo(tipo);
        m.setMonto(new BigDecimal(monto));
        return m;
    }
}
