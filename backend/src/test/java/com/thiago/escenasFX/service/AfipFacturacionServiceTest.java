package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.thiago.escenasFX.service.AfipFacturacionService.DatosFactura;
import com.thiago.escenasFX.service.AfipFacturacionService.ResultadoCae;

/**
 * Sin certificado de homologación no hay forma de probar contra ARCA real (ver plan de
 * facturación fiscal) — estos tests verifican el armado del sobre SOAP que se manda y el parseo
 * de respuestas con la forma documentada de WSFEv1, sin tocar la red.
 */
class AfipFacturacionServiceTest {

    private final AfipFacturacionService service =
        new AfipFacturacionService(RestClient.builder(), mock(AfipAuthService.class), "20300238379");

    private final AfipAuthService.Credenciales cred = new AfipAuthService.Credenciales("TOKEN123", "SIGN123");

    @Test
    void construirEnvelope_conceptoProductos_noIncluyeFechasDeServicio() {
        DatosFactura datos = new DatosFactura(1, 11, 1, 99, "0", new BigDecimal("1500.50"),
            LocalDate.of(2026, 8, 19), null, null, null);

        String envelope = service.construirEnvelopeFECAESolicitar(cred, datos, 5);

        assertThat(envelope).contains("<ar:Token>TOKEN123</ar:Token>");
        assertThat(envelope).contains("<ar:Cuit>20300238379</ar:Cuit>");
        assertThat(envelope).contains("<ar:PtoVta>1</ar:PtoVta>");
        assertThat(envelope).contains("<ar:CbteTipo>11</ar:CbteTipo>");
        assertThat(envelope).contains("<ar:Concepto>1</ar:Concepto>");
        assertThat(envelope).contains("<ar:DocTipo>99</ar:DocTipo>");
        assertThat(envelope).contains("<ar:CbteDesde>5</ar:CbteDesde>");
        assertThat(envelope).contains("<ar:CbteHasta>5</ar:CbteHasta>");
        assertThat(envelope).contains("<ar:CbteFch>20260819</ar:CbteFch>");
        assertThat(envelope).contains("<ar:ImpTotal>1500.50</ar:ImpTotal>");
        assertThat(envelope).contains("<ar:ImpNeto>1500.50</ar:ImpNeto>");
        assertThat(envelope).contains("<ar:ImpIVA>0.00</ar:ImpIVA>");
        assertThat(envelope).doesNotContain("FchServDesde");
    }

    @Test
    void construirEnvelope_conceptoServicios_incluyeFechasDeServicio() {
        LocalDate fecha = LocalDate.of(2026, 8, 19);
        DatosFactura datos = new DatosFactura(1, 11, 3, 80, "20300238379", new BigDecimal("2000"),
            fecha, fecha, fecha, fecha);

        String envelope = service.construirEnvelopeFECAESolicitar(cred, datos, 6);

        assertThat(envelope).contains("<ar:FchServDesde>20260819</ar:FchServDesde>");
        assertThat(envelope).contains("<ar:FchServHasta>20260819</ar:FchServHasta>");
        assertThat(envelope).contains("<ar:FchVtoPago>20260819</ar:FchVtoPago>");
        assertThat(envelope).contains("<ar:DocNro>20300238379</ar:DocNro>");
    }

    @Test
    void parsearResultadoCae_aprobado_extraeCaeYVencimiento() {
        String respuesta = respuestaFecaeSolicitar("A", "5", "70012345678901", "20260829", null, null);

        ResultadoCae resultado = service.parsearResultadoCae(respuesta);

        assertThat(resultado.aprobado()).isTrue();
        assertThat(resultado.numero()).isEqualTo(5);
        assertThat(resultado.cae()).isEqualTo("70012345678901");
        assertThat(resultado.caeVencimiento()).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    void parsearResultadoCae_rechazado_devuelveElMotivoDeLaObservacion() {
        String respuesta = respuestaFecaeSolicitar("R", "5", null, null, "10015", "Factura B (CbteDesde+1) no coincide");

        ResultadoCae resultado = service.parsearResultadoCae(respuesta);

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.cae()).isNull();
        assertThat(resultado.detalle()).contains("10015").contains("no coincide");
    }

    private String respuestaFecaeSolicitar(String resultado, String cbteDesde, String cae, String caeFchVto,
            String obsCode, String obsMsg) {
        String observaciones = obsCode != null
            ? "<Observaciones><Obs><Code>" + obsCode + "</Code><Msg>" + obsMsg + "</Msg></Obs></Observaciones>"
            : "";
        String caeTags = cae != null ? "<CAE>" + cae + "</CAE><CAEFchVto>" + caeFchVto + "</CAEFchVto>" : "";

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body>"
            + "<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
            + "<FECAESolicitarResult>"
            + "<FeCabResp><Cuit>20300238379</Cuit><PtoVta>1</PtoVta><CbteTipo>11</CbteTipo>"
            + "<CantReg>1</CantReg><Resultado>" + resultado + "</Resultado></FeCabResp>"
            + "<FeDetResp><FECAEDetResponse>"
            + "<Concepto>1</Concepto><DocTipo>99</DocTipo><DocNro>0</DocNro>"
            + "<CbteDesde>" + cbteDesde + "</CbteDesde><CbteHasta>" + cbteDesde + "</CbteHasta>"
            + "<CbteFch>20260819</CbteFch><Resultado>" + resultado + "</Resultado>"
            + caeTags + observaciones
            + "</FECAEDetResponse></FeDetResp>"
            + "</FECAESolicitarResult>"
            + "</FECAESolicitarResponse>"
            + "</soap:Body>"
            + "</soap:Envelope>";
    }
}
