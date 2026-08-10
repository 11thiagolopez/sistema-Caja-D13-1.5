package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.DetallePresupuestoRequest;
import com.thiago.escenasFX.dto.PresupuestoRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Presupuesto;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.PresupuestoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class PresupuestoServiceTest {

    @Mock
    private PresupuestoRepository presupuestoRepo;
    @Mock
    private ProductoRepository productoRepo;
    @Mock
    private EmailService emailService;
    @Mock
    private PdfService pdfService;

    @InjectMocks
    private PresupuestoService presupuestoService;

    @BeforeEach
    void configuracionPorDefecto() {
        // lenient: no todos los tests llegan a guardar (algunos tiran excepción antes).
        lenient().when(presupuestoRepo.save(any(Presupuesto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Producto producto(int idProducto, int stockActual) {
        Producto p = new Producto();
        p.setIdProducto(idProducto);
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockActual);
        return p;
    }

    private DetallePresupuestoRequest detalleReqConProducto(int idProducto, int cantidad, String precioUnitario) {
        DetallePresupuestoRequest d = new DetallePresupuestoRequest();
        d.setIdProducto(idProducto);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(new BigDecimal(precioUnitario));
        return d;
    }

    private DetallePresupuestoRequest detalleReqManual(String descripcion, int cantidad, String precioUnitario) {
        DetallePresupuestoRequest d = new DetallePresupuestoRequest();
        d.setDescripcion(descripcion);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(new BigDecimal(precioUnitario));
        return d;
    }

    @Test
    void crear_calculaTotalYNoTocaStockDelProducto() {
        Producto producto = producto(1, 5);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));

        PresupuestoRequest req = new PresupuestoRequest();
        req.setClienteNombre("Cliente de prueba");
        req.setDetalles(List.of(detalleReqConProducto(1, 3, "100")));

        Presupuesto guardado = presupuestoService.crear(req, new Empleado());

        assertThat(guardado.getTotalPresupuesto()).isEqualByComparingTo("300");
        assertThat(guardado.getDetalles().get(0).getSubtotal()).isEqualByComparingTo("300");
        assertThat(guardado.getDetalles().get(0).getDescripcion()).isEqualTo("Producto de prueba");
        // A diferencia de una Venta, un presupuesto es solo informativo: nunca toca stock.
        assertThat(producto.getStockActual()).isEqualTo(5);
        verify(productoRepo, never()).save(any());
    }

    @Test
    void crear_productoNoExiste_lanzaExcepcion() {
        when(productoRepo.findById(99)).thenReturn(Optional.empty());

        PresupuestoRequest req = new PresupuestoRequest();
        req.setClienteNombre("Cliente de prueba");
        req.setDetalles(List.of(detalleReqConProducto(99, 1, "100")));

        assertThatThrownBy(() -> presupuestoService.crear(req, new Empleado()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Producto no existe");

        verify(presupuestoRepo, never()).save(any());
    }

    @Test
    void crear_itemManual_usaLaDescripcionDelRequestSinProducto() {
        PresupuestoRequest req = new PresupuestoRequest();
        req.setClienteNombre("Cliente de prueba");
        req.setDetalles(List.of(detalleReqManual("Apertura de cerradura", 1, "5000")));

        Presupuesto guardado = presupuestoService.crear(req, new Empleado());

        assertThat(guardado.getDetalles().get(0).getProducto()).isNull();
        assertThat(guardado.getDetalles().get(0).getDescripcion()).isEqualTo("Apertura de cerradura");
        assertThat(guardado.getTotalPresupuesto()).isEqualByComparingTo("5000");
        verify(productoRepo, never()).findById(any());
    }

    @Test
    void crear_sinIdProductoNiDescripcion_lanzaExcepcion() {
        PresupuestoRequest req = new PresupuestoRequest();
        req.setClienteNombre("Cliente de prueba");
        req.setDetalles(List.of(detalleReqManual(null, 1, "5000")));

        assertThatThrownBy(() -> presupuestoService.crear(req, new Empleado()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("producto o una descripción manual");
    }

    @Test
    void enviarPorEmail_sinClienteEmail_lanzaExcepcion() {
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setIdPresupuesto(1);
        presupuesto.setClienteEmail(null);
        when(presupuestoRepo.findById(1)).thenReturn(Optional.of(presupuesto));

        assertThatThrownBy(() -> presupuestoService.enviarPorEmail(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no tiene un email");

        verify(emailService, never()).enviarConAdjuntoPdf(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void enviarPorEmail_conClienteEmail_envíaPdfAdjuntoYMarcaEnviado() {
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setIdPresupuesto(1);
        presupuesto.setClienteNombre("Cliente de prueba");
        presupuesto.setClienteEmail("cliente@test.com");
        presupuesto.setTotalPresupuesto(new BigDecimal("300"));
        when(presupuestoRepo.findById(1)).thenReturn(Optional.of(presupuesto));
        byte[] pdfFalso = {1, 2, 3};
        when(pdfService.generarPdf(anyString())).thenReturn(pdfFalso);

        Presupuesto resultado = presupuestoService.enviarPorEmail(1);

        assertThat(resultado.isEnviadoPorEmail()).isTrue();
        verify(emailService).enviarConAdjuntoPdf(eq("cliente@test.com"), anyString(), anyString(),
            eq("presupuesto-1.pdf"), eq(pdfFalso));
    }
}
