package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.exception.AuthenticationFailedException;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepo;
    @Mock
    private ProductoRepository productoRepo;
    @Mock
    private OtpService otpService;
    @Mock
    private EmailService emailService;
    @Mock
    private SesionCajaRepository sesionRepo;

    @InjectMocks
    private VentaService ventaService;

    @BeforeEach
    void configuracionPorDefecto() {
        // lenient: no todos los tests llegan a usar estos stubs (algunos tiran excepción antes).
        lenient().when(sesionRepo.findByEstado(anyString()))
            .thenReturn(Optional.empty());
        lenient().when(ventaRepo.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Producto producto(int idProducto, int stockActual) {
        Producto p = new Producto();
        p.setIdProducto(idProducto);
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockActual);
        return p;
    }

    private Venta ventaCon(DetalleVenta... detalles) {
        Venta venta = new Venta();
        venta.setDetalles(new ArrayList<>(List.of(detalles)));
        return venta;
    }

    private DetalleVenta detalle(Producto producto, int cantidad, BigDecimal precioUnitario) {
        DetalleVenta d = new DetalleVenta();
        d.setProducto(producto);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precioUnitario);
        return d;
    }

    @Test
    void registrarVenta_sinDescuento_confirmaYDescuentaStock() {
        Producto producto = producto(1, 10);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));

        Venta venta = ventaCon(detalle(producto, 3, new BigDecimal("100")));

        Venta guardada = ventaService.registrarVenta(venta);

        assertThat(guardada.getEstado()).isEqualTo("CONFIRMADA");
        assertThat(guardada.getTotalVenta()).isEqualByComparingTo("300");
        assertThat(producto.getStockActual()).isEqualTo(7);
        assertThat(guardada.getDetalles().get(0).getSubtotal()).isEqualByComparingTo("300");
        verify(productoRepo).save(producto);
        verify(emailService, never()).enviarOtpAAdmins(anyString(), anyString());
    }

    @Test
    void registrarVenta_stockInsuficiente_lanzaExcepcionYNoGuardaNada() {
        Producto producto = producto(1, 2);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));

        Venta venta = ventaCon(detalle(producto, 5, new BigDecimal("100")));

        assertThatThrownBy(() -> ventaService.registrarVenta(venta))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Stock insuficiente");

        verify(ventaRepo, never()).save(any());
        verify(productoRepo, never()).save(any());
    }

    @Test
    void registrarVenta_descuentoNegativo_lanzaExcepcion() {
        Producto producto = producto(1, 10);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));

        Venta venta = ventaCon(detalle(producto, 1, new BigDecimal("100")));
        venta.setDescuento(new BigDecimal("-10"));

        assertThatThrownBy(() -> ventaService.registrarVenta(venta))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no puede ser negativo");
    }

    @Test
    void registrarVenta_descuentoMayorAlTotal_lanzaExcepcion() {
        Producto producto = producto(1, 10);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));

        Venta venta = ventaCon(detalle(producto, 1, new BigDecimal("100")));
        venta.setDescuento(new BigDecimal("150"));
        venta.setMotivoDescuento("motivo");

        assertThatThrownBy(() -> ventaService.registrarVenta(venta))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no puede ser mayor al total");
    }

    @Test
    void registrarVenta_descuentoSinMotivo_lanzaExcepcion() {
        Producto producto = producto(1, 10);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));

        Venta venta = ventaCon(detalle(producto, 1, new BigDecimal("100")));
        venta.setDescuento(new BigDecimal("10"));

        assertThatThrownBy(() -> ventaService.registrarVenta(venta))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requiere indicar un motivo");
    }

    @Test
    void registrarVenta_conDescuentoValido_quedaPendienteYEnviaOtp() {
        Producto producto = producto(1, 10);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));
        when(otpService.generarCodigo()).thenReturn("123456");
        when(otpService.hash("123456")).thenReturn("hash-otp");
        LocalDateTime expiracion = LocalDateTime.now().plusMinutes(10);
        when(otpService.nuevaExpiracion()).thenReturn(expiracion);

        Venta venta = ventaCon(detalle(producto, 2, new BigDecimal("100")));
        venta.setDescuento(new BigDecimal("50"));
        venta.setMotivoDescuento("Cliente frecuente");

        Venta guardada = ventaService.registrarVenta(venta);

        assertThat(guardada.getEstado()).isEqualTo("PENDIENTE_AUTORIZACION");
        assertThat(guardada.getTotalVenta()).isEqualByComparingTo("150");
        assertThat(guardada.getOtpHash()).isEqualTo("hash-otp");
        assertThat(guardada.getOtpExpiraEn()).isEqualTo(expiracion);
        // El stock se descuenta igual, aunque la venta quede pendiente de autorización.
        assertThat(producto.getStockActual()).isEqualTo(8);
        verify(emailService).enviarOtpAAdmins(anyString(), anyString());
    }

    @Test
    void registrarVenta_vinculaLaSesionAbiertaSiExiste() {
        Producto producto = producto(1, 10);
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto));
        SesionCaja sesionAbierta = new SesionCaja();
        sesionAbierta.setIdSesion(7);
        when(sesionRepo.findByEstado(anyString()))
            .thenReturn(Optional.of(sesionAbierta));

        Venta venta = ventaCon(detalle(producto, 1, new BigDecimal("100")));

        Venta guardada = ventaService.registrarVenta(venta);

        assertThat(guardada.getSesion()).isEqualTo(sesionAbierta);
    }

    @Test
    void confirmarDescuento_codigoCorrecto_confirmaVenta() {
        Venta venta = new Venta();
        venta.setEstado("PENDIENTE_AUTORIZACION");
        venta.setOtpHash("hash-otp");
        venta.setOtpExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));
        when(otpService.coincide("123456", "hash-otp")).thenReturn(true);

        Venta confirmada = ventaService.confirmarDescuento(1, "123456");

        assertThat(confirmada.getEstado()).isEqualTo("CONFIRMADA");
        assertThat(confirmada.getOtpHash()).isNull();
        assertThat(confirmada.getOtpExpiraEn()).isNull();
    }

    @Test
    void confirmarDescuento_codigoIncorrecto_lanzaAuthenticationFailedException() {
        Venta venta = new Venta();
        venta.setEstado("PENDIENTE_AUTORIZACION");
        venta.setOtpHash("hash-otp");
        venta.setOtpExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));
        when(otpService.coincide("000000", "hash-otp")).thenReturn(false);

        assertThatThrownBy(() -> ventaService.confirmarDescuento(1, "000000"))
            .isInstanceOf(AuthenticationFailedException.class);

        verify(ventaRepo, never()).save(any());
    }

    @Test
    void confirmarDescuento_otpExpirado_lanzaIllegalStateException() {
        Venta venta = new Venta();
        venta.setEstado("PENDIENTE_AUTORIZACION");
        venta.setOtpHash("hash-otp");
        venta.setOtpExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> ventaService.confirmarDescuento(1, "123456"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("expiró");

        verify(ventaRepo, never()).save(any());
    }

    @Test
    void confirmarDescuento_ventaNoEstaPendiente_lanzaIllegalStateException() {
        Venta venta = new Venta();
        venta.setEstado("CONFIRMADA");
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> ventaService.confirmarDescuento(1, "123456"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no tiene un descuento pendiente");
    }
}
