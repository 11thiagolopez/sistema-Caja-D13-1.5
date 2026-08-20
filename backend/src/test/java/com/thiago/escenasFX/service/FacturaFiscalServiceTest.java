package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.VentaRepository;
import com.thiago.escenasFX.service.AfipFacturacionService.ResultadoCae;

@ExtendWith(MockitoExtension.class)
class FacturaFiscalServiceTest {

    @Mock
    private FacturaFiscalRepository facturaRepo;
    @Mock
    private VentaRepository ventaRepo;
    @Mock
    private AfipFacturacionService afipFacturacionService;

    private FacturaFiscalService facturaFiscalService;

    @BeforeEach
    void setUp() {
        facturaFiscalService = new FacturaFiscalService(facturaRepo, ventaRepo, afipFacturacionService, 1);
        lenient().when(facturaRepo.save(any(FacturaFiscal.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(facturaRepo.findByVentaIdVenta(anyInt())).thenReturn(Optional.empty());
    }

    private Venta ventaConfirmada(String tipoDetalle) {
        Venta venta = new Venta();
        venta.setIdVenta(10);
        venta.setEstado("CONFIRMADA");
        venta.setFecha(LocalDateTime.of(2026, 8, 19, 15, 0));
        venta.setTotalVenta(new BigDecimal("1500.00"));
        DetalleVenta detalle = new DetalleVenta();
        detalle.setTipo(tipoDetalle);
        venta.setDetalles(List.of(detalle));
        return venta;
    }

    @Test
    void facturar_ventaConfirmadaConsumidorFinal_emiteYGuardaEmitida() {
        Venta venta = ventaConfirmada("ARTICULO");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(true, 7, "70099998887776", LocalDate.of(2026, 8, 29), null));

        FacturaFiscal factura = facturaFiscalService.facturar(10, 99, null);

        assertThat(factura.getEstado()).isEqualTo("EMITIDA");
        assertThat(factura.getCae()).isEqualTo("70099998887776");
        assertThat(factura.getNumero()).isEqualTo(7);
        assertThat(factura.getTipoComprobante()).isEqualTo(11);
        assertThat(factura.getPuntoVenta()).isEqualTo(1);
    }

    @Test
    void facturar_ventaNoConfirmada_tiraIllegalStateException() {
        Venta venta = ventaConfirmada("ARTICULO");
        venta.setEstado("PENDIENTE_AUTORIZACION");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> facturaFiscalService.facturar(10, 99, null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void facturar_yaTieneFacturaEmitida_tiraIllegalStateException() {
        Venta venta = ventaConfirmada("ARTICULO");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));
        FacturaFiscal existente = new FacturaFiscal();
        existente.setEstado("EMITIDA");
        when(facturaRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> facturaFiscalService.facturar(10, 99, null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void facturar_clienteConCuitSinDocNro_tiraIllegalArgumentException() {
        Venta venta = ventaConfirmada("ARTICULO");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> facturaFiscalService.facturar(10, 80, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void facturar_arcaRechaza_guardaEstadoErrorConDetalle() {
        Venta venta = ventaConfirmada("ARTICULO");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(false, null, null, null, "10015 - Observación de ARCA"));

        FacturaFiscal factura = facturaFiscalService.facturar(10, 99, null);

        assertThat(factura.getEstado()).isEqualTo("ERROR");
        assertThat(factura.getErrorDetalle()).contains("10015");
        assertThat(factura.getCae()).isNull();
    }

    @Test
    void facturar_fallaLaConexion_guardaEstadoErrorSinPropagarLaExcepcion() {
        Venta venta = ventaConfirmada("ARTICULO");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));
        when(afipFacturacionService.emitirFacturaC(any()))
            .thenThrow(new com.thiago.escenasFX.exception.AfipIntegracionException("timeout"));

        FacturaFiscal factura = facturaFiscalService.facturar(10, 99, null);

        assertThat(factura.getEstado()).isEqualTo("ERROR");
        assertThat(factura.getErrorDetalle()).isEqualTo("timeout");
    }

    @Test
    void facturar_ventaSoloConServicios_usaConceptoServicios() {
        Venta venta = ventaConfirmada("SERVICIO");
        when(ventaRepo.findById(10)).thenReturn(Optional.of(venta));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(true, 1, "cae", LocalDate.now(), null));

        facturaFiscalService.facturar(10, 99, null);

        var captor = org.mockito.ArgumentCaptor.forClass(AfipFacturacionService.DatosFactura.class);
        org.mockito.Mockito.verify(afipFacturacionService).emitirFacturaC(captor.capture());
        assertThat(captor.getValue().concepto()).isEqualTo(2);
        assertThat(captor.getValue().fchServDesde()).isNotNull();
    }
}
