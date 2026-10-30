package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.DetalleNotaCreditoRequest;
import com.thiago.escenasFX.dto.NotaCreditoRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.DetalleNotaCreditoRepository;
import com.thiago.escenasFX.repository.DetalleVentaRepository;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class NotaCreditoServiceTest {

    @Mock
    private NotaCreditoRepository notaCreditoRepo;
    @Mock
    private DetalleNotaCreditoRepository detalleNotaCreditoRepo;
    @Mock
    private VentaRepository ventaRepo;
    @Mock
    private DetalleVentaRepository detalleVentaRepo;
    @Mock
    private ProductoRepository productoRepo;
    @Mock
    private ClienteRepository clienteRepo;
    @Mock
    private FacturaFiscalRepository facturaFiscalRepo;
    @Mock
    private MovimientoCuentaCorrienteClienteRepository movimientoRepo;
    @Mock
    private PdfService pdfService;

    @InjectMocks
    private NotaCreditoService notaCreditoService;

    private final Empleado empleado = empleado(1, "Admin de prueba");

    @BeforeEach
    void configuracionPorDefecto() {
        lenient().when(notaCreditoRepo.save(any(NotaCredito.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(detalleNotaCreditoRepo.sumarCantidadAcreditada(any())).thenReturn(0);
        lenient().when(facturaFiscalRepo.findByVentaIdVenta(any())).thenReturn(Optional.empty());
    }

    private Empleado empleado(int id, String nombre) {
        Empleado e = new Empleado();
        e.setIdEmpleado(id);
        e.setNombre(nombre);
        return e;
    }

    private Producto producto(int id, int stockActual) {
        Producto p = new Producto();
        p.setIdProducto(id);
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockActual);
        return p;
    }

    private DetalleVenta detalleVenta(int idDetalle, Venta venta, Producto producto, int cantidad, String precio) {
        DetalleVenta d = new DetalleVenta();
        d.setIdDetalle(idDetalle);
        d.setVenta(venta);
        d.setProducto(producto);
        d.setDescripcion(producto != null ? producto.getDescripcion() : "Mano de obra");
        d.setCantidad(cantidad);
        d.setPrecioUnitario(new BigDecimal(precio));
        return d;
    }

    private Venta ventaConfirmada(int idVenta, DetalleVenta... detalles) {
        Venta venta = new Venta();
        venta.setIdVenta(idVenta);
        venta.setEstado("CONFIRMADA");
        venta.setDetalles(new ArrayList<>(List.of(detalles)));
        return venta;
    }

    private Cliente cliente(int id) {
        Cliente c = new Cliente();
        c.setIdCliente(id);
        c.setNombre("Cliente de prueba");
        return c;
    }

    private NotaCreditoRequest requestParcial(int idVenta, String motivo, DetalleNotaCreditoRequest... detalles) {
        NotaCreditoRequest req = new NotaCreditoRequest();
        req.setIdVentaOriginal(idVenta);
        req.setIdEmpleado(1);
        req.setIdCliente(10);
        req.setMotivo(motivo);
        req.setAlcance("PARCIAL");
        req.setDetalles(List.of(detalles));
        return req;
    }

    private DetalleNotaCreditoRequest lineaReq(int idDetalle, int cantidad) {
        DetalleNotaCreditoRequest d = new DetalleNotaCreditoRequest();
        d.setIdDetalleVentaOriginal(idDetalle);
        d.setCantidad(cantidad);
        return d;
    }

    @Test
    void crear_motivoDevolucionMercaderia_devuelveStockDeLaLineaAcreditada() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(productoRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(producto));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));

        NotaCredito nc = notaCreditoService.crear(
            requestParcial(100, "DEVOLUCION_MERCADERIA", lineaReq(1, 2)), empleado);

        assertThat(producto.getStockActual()).isEqualTo(7); // 5 + 2 devueltas
        assertThat(nc.getMontoTotal()).isEqualByComparingTo("2000"); // 2 * 1000
        assertThat(nc.getDetalles().get(0).getCantidad()).isEqualTo(2);
        verify(productoRepo).save(producto);
    }

    @Test
    void crear_motivoErrorTipeo_noTocaStockAunqueHayaProducto() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));

        notaCreditoService.crear(requestParcial(100, "ERROR_TIPEO", lineaReq(1, 1)), empleado);

        assertThat(producto.getStockActual()).isEqualTo(5); // sin cambios
        verify(productoRepo, never()).buscarPorIdConLock(any());
        verify(productoRepo, never()).save(any());
    }

    @Test
    void crear_alcanceTotal_expandeATodasLasLineasPorElRemanente() {
        Producto p1 = producto(1, 5);
        Producto p2 = producto(2, 8);
        Venta venta = ventaConfirmada(100,
            detalleVenta(1, null, p1, 2, "100"),
            detalleVenta(2, null, p2, 1, "500"));
        venta.getDetalles().forEach(d -> d.setVenta(venta));
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(detalleVentaRepo.findById(2)).thenReturn(Optional.of(venta.getDetalles().get(1)));
        when(productoRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(p1));
        when(productoRepo.buscarPorIdConLock(2)).thenReturn(Optional.of(p2));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));

        NotaCreditoRequest req = new NotaCreditoRequest();
        req.setIdVentaOriginal(100);
        req.setIdEmpleado(1);
        req.setIdCliente(10);
        req.setMotivo("ANULACION_TOTAL");
        req.setAlcance("TOTAL");

        NotaCredito nc = notaCreditoService.crear(req, empleado);

        assertThat(p1.getStockActual()).isEqualTo(7); // 5 + 2
        assertThat(p2.getStockActual()).isEqualTo(9); // 8 + 1
        assertThat(nc.getMontoTotal()).isEqualByComparingTo("700"); // 200 + 500
        assertThat(nc.getDetalles()).hasSize(2);
    }

    @Test
    void crear_pideMasQueElRemanente_lanzaExcepcion() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));
        // Ya se acreditaron 2 de las 3 unidades por una nota de crédito anterior.
        when(detalleNotaCreditoRepo.sumarCantidadAcreditada(1)).thenReturn(2);

        assertThatThrownBy(() -> notaCreditoService.crear(
                requestParcial(100, "DEVOLUCION_MERCADERIA", lineaReq(1, 2)), empleado))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("quedan 1 disponibles");

        verify(notaCreditoRepo, never()).save(any());
        verify(productoRepo, never()).buscarPorIdConLock(any());
    }

    @Test
    void crear_ventaNoConfirmada_lanzaExcepcion() {
        Venta venta = ventaConfirmada(100);
        venta.setEstado("PENDIENTE_AUTORIZACION");
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> notaCreditoService.crear(
                requestParcial(100, "ERROR_TIPEO", lineaReq(1, 1)), empleado))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CONFIRMADA");
    }

    @Test
    void crear_sinClienteEnElRequestNiEnLaVenta_lanzaExcepcion() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));

        NotaCreditoRequest req = requestParcial(100, "ERROR_TIPEO", lineaReq(1, 1));
        req.setIdCliente(null);

        assertThatThrownBy(() -> notaCreditoService.crear(req, empleado))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("indicar un cliente");
    }

    @Test
    void crear_creaElMovimientoCreditoConElMontoCorrecto() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(productoRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(producto));
        Cliente cliente = cliente(10);
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente));

        notaCreditoService.crear(requestParcial(100, "DEVOLUCION_MERCADERIA", lineaReq(1, 2)), empleado);

        org.mockito.ArgumentCaptor<MovimientoCuentaCorrienteCliente> captor =
            org.mockito.ArgumentCaptor.forClass(MovimientoCuentaCorrienteCliente.class);
        verify(movimientoRepo).save(captor.capture());
        MovimientoCuentaCorrienteCliente movimiento = captor.getValue();
        assertThat(movimiento.getTipo()).isEqualTo("CREDITO");
        assertThat(movimiento.getMonto()).isEqualByComparingTo("2000");
        assertThat(movimiento.getCliente()).isEqualTo(cliente);
    }

    @Test
    void crear_ventaConFacturaFiscalEmitida_marcaEsFiscalTrue() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));
        when(productoRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(producto));

        FacturaFiscal factura = new FacturaFiscal();
        factura.setEstado("EMITIDA");
        when(facturaFiscalRepo.findByVentaIdVenta(100)).thenReturn(Optional.of(factura));

        NotaCredito nc = notaCreditoService.crear(
            requestParcial(100, "DEVOLUCION_MERCADERIA", lineaReq(1, 1)), empleado);

        assertThat(nc.isEsFiscal()).isTrue();
        // Falta la llamada a ARCA (NotaCreditoFiscalService.emitir) — no puede quedar "EMITIDA".
        assertThat(nc.getEstado()).isEqualTo("PENDIENTE_FISCAL");
    }

    @Test
    void crear_ventaSinFacturaFiscal_marcaEsFiscalFalse() {
        Producto producto = producto(1, 5);
        Venta venta = ventaConfirmada(100, detalleVenta(1, null, producto, 3, "1000"));
        venta.getDetalles().get(0).setVenta(venta);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(venta.getDetalles().get(0)));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));
        when(productoRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(producto));

        NotaCredito nc = notaCreditoService.crear(
            requestParcial(100, "DEVOLUCION_MERCADERIA", lineaReq(1, 1)), empleado);

        assertThat(nc.isEsFiscal()).isFalse();
        assertThat(nc.getEstado()).isEqualTo("EMITIDA"); // interna: no hay nada más que hacer.
    }

    @Test
    void crear_lineaNoPerteneceALaVenta_lanzaExcepcion() {
        Venta otraVenta = ventaConfirmada(200);
        Producto producto = producto(1, 5);
        DetalleVenta lineaDeOtraVenta = detalleVenta(1, otraVenta, producto, 1, "1000");
        Venta venta = ventaConfirmada(100);
        when(ventaRepo.findById(100)).thenReturn(Optional.of(venta));
        when(detalleVentaRepo.findById(1)).thenReturn(Optional.of(lineaDeOtraVenta));
        when(clienteRepo.findById(10)).thenReturn(Optional.of(cliente(10)));

        assertThatThrownBy(() -> notaCreditoService.crear(
                requestParcial(100, "ERROR_TIPEO", lineaReq(1, 1)), empleado))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no pertenece a la venta");
    }
}
