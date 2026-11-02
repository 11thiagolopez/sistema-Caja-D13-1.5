package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.model.DetalleNotaCredito;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.service.AfipFacturacionService.DatosFactura;
import com.thiago.escenasFX.service.AfipFacturacionService.ResultadoCae;

@ExtendWith(MockitoExtension.class)
class NotaCreditoFiscalServiceTest {

    @Mock
    private NotaCreditoRepository notaCreditoRepo;
    @Mock
    private FacturaFiscalRepository facturaFiscalRepo;
    @Mock
    private AfipFacturacionService afipFacturacionService;
    @Mock
    private AfipPadronService afipPadronService;

    private NotaCreditoFiscalService service;

    @BeforeEach
    void setUp() {
        service = new NotaCreditoFiscalService(notaCreditoRepo, facturaFiscalRepo, afipFacturacionService, afipPadronService, 1);
        lenient().when(notaCreditoRepo.save(any(NotaCredito.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Venta venta(int idVenta) {
        Venta v = new Venta();
        v.setIdVenta(idVenta);
        return v;
    }

    private FacturaFiscal facturaEmitida() {
        FacturaFiscal f = new FacturaFiscal();
        f.setEstado("EMITIDA");
        f.setPuntoVenta(1);
        f.setTipoComprobante(11);
        f.setNumero(55);
        f.setClienteDocTipo(99);
        f.setClienteDocNro(null);
        return f;
    }

    private NotaCredito notaCreditoFiscal(Venta ventaOriginal) {
        DetalleVenta detalleOriginal = new DetalleVenta();
        detalleOriginal.setTipo("ARTICULO");

        DetalleNotaCredito detalle = new DetalleNotaCredito();
        detalle.setDetalleVentaOriginal(detalleOriginal);

        NotaCredito nc = new NotaCredito();
        nc.setIdNotaCredito(1);
        nc.setVentaOriginal(ventaOriginal);
        nc.setEsFiscal(true);
        nc.setEstado("PENDIENTE_FISCAL");
        nc.setMontoTotal(new BigDecimal("500"));
        nc.setFecha(LocalDateTime.of(2026, 10, 30, 12, 0));
        nc.setDetalles(List.of(detalle));
        return nc;
    }

    @Test
    void emitir_notaFiscalConFacturaOriginalEmitida_emiteYGuardaCae() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(facturaEmitida()));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(true, 8, "70099998887776", LocalDate.of(2026, 11, 9), null));

        NotaCredito resultado = service.emitir(1);

        assertThat(resultado.getEstado()).isEqualTo("EMITIDA");
        assertThat(resultado.getCae()).isEqualTo("70099998887776");
        assertThat(resultado.getNumero()).isEqualTo(8);
        assertThat(resultado.getTipoComprobante()).isEqualTo(13);
        assertThat(resultado.getPuntoVenta()).isEqualTo(1);
    }

    @Test
    void emitir_pasaElComprobanteAsociadoDeLaFacturaOriginal() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(facturaEmitida()));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(true, 8, "cae", LocalDate.now(), null));

        service.emitir(1);

        ArgumentCaptor<DatosFactura> captor = ArgumentCaptor.forClass(DatosFactura.class);
        verify(afipFacturacionService).emitirFacturaC(captor.capture());
        DatosFactura datos = captor.getValue();
        assertThat(datos.cbteTipo()).isEqualTo(13);
        assertThat(datos.cbteAsocTipo()).isEqualTo(11); // Factura C original
        assertThat(datos.cbteAsocPtoVta()).isEqualTo(1);
        assertThat(datos.cbteAsocNro()).isEqualTo(55);
        assertThat(datos.concepto()).isEqualTo(1); // la única línea es ARTICULO
    }

    private DatosFactura emitirYCapturar(FacturaFiscal factura) {
        NotaCredito nc = notaCreditoFiscal(venta(10));
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(factura));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(true, 8, "cae", LocalDate.now(), null));

        service.emitir(1);

        ArgumentCaptor<DatosFactura> captor = ArgumentCaptor.forClass(DatosFactura.class);
        verify(afipFacturacionService).emitirFacturaC(captor.capture());
        return captor.getValue();
    }

    @Test
    void emitir_usaLaCondicionIvaGuardadaEnLaFactura() {
        FacturaFiscal factura = facturaEmitida();
        factura.setClienteDocTipo(80);
        factura.setClienteDocNro("20300238379");
        factura.setCondicionIvaReceptor(1);

        assertThat(emitirYCapturar(factura).condicionIvaReceptor()).isEqualTo(1);
        verify(afipPadronService, never()).consultar(any());
    }

    @Test
    void emitir_facturaViejaConsumidorFinal_informaConsumidorFinal() {
        assertThat(emitirYCapturar(facturaEmitida()).condicionIvaReceptor()).isEqualTo(5);
    }

    @Test
    void emitir_facturaViejaConCuit_leConsultaLaCondicionAlPadron() {
        FacturaFiscal factura = facturaEmitida();
        factura.setClienteDocTipo(80);
        factura.setClienteDocNro("20300238379");
        when(afipPadronService.consultar("20300238379")).thenReturn(new AfipPadronService.Contribuyente(
            "20300238379", "Juan", "FISICA", null, "ACTIVO", 6, null));

        assertThat(emitirYCapturar(factura).condicionIvaReceptor()).isEqualTo(6);
    }

    @Test
    void emitir_padronFalla_quedaEnErrorSinLlamarAWsfe() {
        FacturaFiscal factura = facturaEmitida();
        factura.setClienteDocTipo(80);
        factura.setClienteDocNro("20300238379");
        NotaCredito nc = notaCreditoFiscal(venta(10));
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(factura));
        when(afipPadronService.consultar("20300238379")).thenThrow(new RuntimeException("sin autorización"));

        NotaCredito resultado = service.emitir(1);

        assertThat(resultado.getEstado()).isEqualTo("ERROR");
        assertThat(resultado.getErrorDetalle()).isEqualTo("sin autorización");
        verify(afipFacturacionService, never()).emitirFacturaC(any());
    }

    @Test
    void emitir_notaInterna_lanzaExcepcionSinLlamarAAfip() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        nc.setEsFiscal(false);
        nc.setEstado("EMITIDA");
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));

        assertThatThrownBy(() -> service.emitir(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("interna");

        verify(afipFacturacionService, never()).emitirFacturaC(any());
    }

    @Test
    void emitir_yaEmitidaAnteArca_lanzaExcepcion() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        nc.setEstado("EMITIDA");
        nc.setCae("cae-existente");
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));

        assertThatThrownBy(() -> service.emitir(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ya fue emitida");

        verify(afipFacturacionService, never()).emitirFacturaC(any());
    }

    @Test
    void emitir_ventaOriginalSinFacturaEmitida_lanzaExcepcion() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("factura fiscal EMITIDA");
    }

    @Test
    void emitir_arcaRechaza_guardaEstadoErrorConDetalleYEsReintentable() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(facturaEmitida()));
        when(afipFacturacionService.emitirFacturaC(any())).thenReturn(
            new ResultadoCae(false, null, null, null, "10062 - Observación de ARCA"));

        NotaCredito resultado = service.emitir(1);

        assertThat(resultado.getEstado()).isEqualTo("ERROR");
        assertThat(resultado.getErrorDetalle()).contains("10062");
        assertThat(resultado.getCae()).isNull();
    }

    @Test
    void emitir_fallaLaConexion_guardaEstadoErrorSinPropagarLaExcepcion() {
        Venta ventaOriginal = venta(10);
        NotaCredito nc = notaCreditoFiscal(ventaOriginal);
        when(notaCreditoRepo.findById(1)).thenReturn(Optional.of(nc));
        when(facturaFiscalRepo.findByVentaIdVenta(10)).thenReturn(Optional.of(facturaEmitida()));
        when(afipFacturacionService.emitirFacturaC(any()))
            .thenThrow(new com.thiago.escenasFX.exception.AfipIntegracionException("timeout"));

        NotaCredito resultado = service.emitir(1);

        assertThat(resultado.getEstado()).isEqualTo("ERROR");
        assertThat(resultado.getErrorDetalle()).isEqualTo("timeout");
    }
}
