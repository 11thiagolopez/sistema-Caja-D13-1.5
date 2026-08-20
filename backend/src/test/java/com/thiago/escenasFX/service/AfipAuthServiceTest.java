package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.thiago.escenasFX.exception.AfipIntegracionException;

/**
 * No hay certificado de homologación de ARCA para probar contra la red real (ver plan de
 * facturación fiscal) — estos tests verifican la parte que sí se puede validar sin red: el armado
 * del LoginTicketRequest y el parseo de una respuesta de WSAA con la forma documentada.
 */
class AfipAuthServiceTest {

    private final AfipAuthService service = new AfipAuthService(RestClient.builder(), "", "", "", "");

    @Test
    void construirLoginTicketRequestXml_incluyeHeaderYServicioWsfe() {
        ZonedDateTime ahora = ZonedDateTime.of(2026, 8, 19, 10, 0, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires"));

        String xml = service.construirLoginTicketRequestXml(ahora);

        assertThat(xml).contains("<service>wsfe</service>");
        assertThat(xml).contains("<uniqueId>" + ahora.toEpochSecond() + "</uniqueId>");
        assertThat(xml).contains("<generationTime>2026-08-19T09:50:00-03:00</generationTime>");
        assertThat(xml).contains("<expirationTime>2026-08-19T10:10:00-03:00</expirationTime>");
    }

    @Test
    void parsearCredenciales_extraeTokenYSignDeLaRespuestaAnidada() {
        String respuesta = respuestaLoginCms("TOKEN123", "SIGN123", "2026-08-19T22:00:00-03:00");

        AfipAuthService.Credenciales credenciales = service.parsearCredenciales(respuesta);

        assertThat(credenciales.token()).isEqualTo("TOKEN123");
        assertThat(credenciales.sign()).isEqualTo("SIGN123");
    }

    @Test
    void parsearVencimiento_leeElExpirationTimeDelHeaderInterno() {
        String respuesta = respuestaLoginCms("TOKEN123", "SIGN123", "2026-08-19T22:00:00-03:00");

        var vencimiento = service.parsearVencimiento(respuesta);

        assertThat(vencimiento).isEqualTo(ZonedDateTime.parse("2026-08-19T22:00:00-03:00").toInstant());
    }

    @Test
    void parsearCredenciales_sinLoginCmsReturn_tiraAfipIntegracionException() {
        String respuestaInvalida = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Body><algoDistinto/></soapenv:Body></soapenv:Envelope>";

        assertThatThrownBy(() -> service.parsearCredenciales(respuestaInvalida))
            .isInstanceOf(AfipIntegracionException.class);
    }

    private String respuestaLoginCms(String token, String sign, String expirationTime) {
        String loginTicketResponse = "&lt;?xml version=\"1.0\" encoding=\"UTF-8\"?&gt;"
            + "&lt;loginTicketResponse version=\"1.0\"&gt;"
            + "&lt;header&gt;"
            + "&lt;uniqueId&gt;123&lt;/uniqueId&gt;"
            + "&lt;generationTime&gt;2026-08-19T09:50:00-03:00&lt;/generationTime&gt;"
            + "&lt;expirationTime&gt;" + expirationTime + "&lt;/expirationTime&gt;"
            + "&lt;/header&gt;"
            + "&lt;credentials&gt;&lt;token&gt;" + token + "&lt;/token&gt;&lt;sign&gt;" + sign + "&lt;/sign&gt;&lt;/credentials&gt;"
            + "&lt;/loginTicketResponse&gt;";

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Body>"
            + "<loginCmsResponse xmlns=\"http://wsaa.view.sua.dvadac.afip.gov.ar/\">"
            + "<loginCmsReturn>" + loginTicketResponse + "</loginCmsReturn>"
            + "</loginCmsResponse>"
            + "</soapenv:Body>"
            + "</soapenv:Envelope>";
    }
}
