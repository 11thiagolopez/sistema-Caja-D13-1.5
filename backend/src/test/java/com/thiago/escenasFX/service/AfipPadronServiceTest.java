package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.thiago.escenasFX.exception.AfipIntegracionException;
import com.thiago.escenasFX.service.AfipPadronService.Contribuyente;

/**
 * Sin red: arma el pedido y parsea respuestas con la forma documentada en el manual oficial de
 * ws_sr_constancia_inscripcion v3.4. No hay homologación, así que el servicio real recién se
 * ejercita al usarlo en producción.
 */
class AfipPadronServiceTest {

    private static final String CUIT = "20300238379";

    private final AfipAuthService authService = mock(AfipAuthService.class);
    private final AfipPadronService service = new AfipPadronService(RestClient.builder(), authService, "20300238379");

    private String respuesta(String contenidoPersona) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
            + "<ns2:getPersona_v2Response xmlns:ns2=\"http://a5.soap.ws.server.puc.sr/\"><personaReturn>"
            + contenidoPersona
            + "</personaReturn></ns2:getPersona_v2Response></soap:Body></soap:Envelope>";
    }

    private String generalesPersonaFisica() {
        return "<datosGenerales><apellido>MICHELLE ELIZABETH</apellido>"
            + "<domicilioFiscal><descripcionProvincia>CORDOBA</descripcionProvincia>"
            + "<direccion>SANTA FE 7516</direccion><localidad>BARRIO YAPEYU                    *</localidad>"
            + "</domicilioFiscal><estadoClave>ACTIVO</estadoClave><nombre>FELIX</nombre>"
            + "<tipoPersona>FISICA</tipoPersona></datosGenerales>";
    }

    @Test
    void esCuitValido_verificaElDigitoVerificador() {
        assertThat(AfipPadronService.esCuitValido("20300238379")).isTrue();
        assertThat(AfipPadronService.esCuitValido("20300238378")).isFalse();
        assertThat(AfipPadronService.esCuitValido("2030023837")).isFalse();
        assertThat(AfipPadronService.esCuitValido("no-es-cuit")).isFalse();
        assertThat(AfipPadronService.esCuitValido(null)).isFalse();
    }

    @Test
    void consultar_cuitInvalido_rechazaSinLlamarAArca() {
        assertThatThrownBy(() -> service.consultar("20300238378"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no es válido");

        verifyNoInteractions(authService);
    }

    @Test
    void construirEnvelope_llevaLosCamposDeGetPersonaV2() {
        String envelope = service.construirEnvelope(new AfipAuthService.Credenciales("TOKEN", "SIGN"), "27298672478");

        assertThat(envelope).contains("xmlns:a5=\"http://a5.soap.ws.server.puc.sr/\"");
        assertThat(envelope).containsSubsequence("<a5:getPersona_v2>", "<token>TOKEN</token>", "<sign>SIGN</sign>",
            "<cuitRepresentada>20300238379</cuitRepresentada>", "<idPersona>27298672478</idPersona>");
    }

    @Test
    void parsearPersona_personaFisica_armaNombreYDomicilioLimpio() {
        Contribuyente c = service.parsearPersona(respuesta(generalesPersonaFisica()), CUIT);

        assertThat(c.nombre()).isEqualTo("MICHELLE ELIZABETH FELIX");
        assertThat(c.tipoPersona()).isEqualTo("FISICA");
        assertThat(c.estadoClave()).isEqualTo("ACTIVO");
        assertThat(c.domicilio()).isEqualTo("SANTA FE 7516, BARRIO YAPEYU, CORDOBA");
        assertThat(c.condicionIva()).isNull();
        assertThat(c.advertencia()).isNull();
    }

    @Test
    void parsearPersona_personaJuridica_usaLaRazonSocial() {
        Contribuyente c = service.parsearPersona(respuesta(
            "<datosGenerales><razonSocial>EJEMPLO SA</razonSocial><tipoPersona>JURIDICA</tipoPersona></datosGenerales>"),
            CUIT);

        assertThat(c.nombre()).isEqualTo("EJEMPLO SA");
    }

    @Test
    void parsearPersona_deduceLaCondicionIvaDeLosImpuestos() {
        String monotributo = generalesPersonaFisica()
            + "<datosMonotributo><impuesto><idImpuesto>20</idImpuesto></impuesto></datosMonotributo>";
        String inscripto = generalesPersonaFisica()
            + "<datosRegimenGeneral><impuesto><idImpuesto>11</idImpuesto></impuesto>"
            + "<impuesto><idImpuesto>30</idImpuesto></impuesto></datosRegimenGeneral>";
        String exento = generalesPersonaFisica()
            + "<datosRegimenGeneral><impuesto><idImpuesto>32</idImpuesto></impuesto></datosRegimenGeneral>";

        assertThat(service.parsearPersona(respuesta(monotributo), CUIT).condicionIva()).isEqualTo(6);
        assertThat(service.parsearPersona(respuesta(inscripto), CUIT).condicionIva()).isEqualTo(1);
        assertThat(service.parsearPersona(respuesta(exento), CUIT).condicionIva()).isEqualTo(4);
    }

    @Test
    void parsearPersona_constanciaObjetada_devuelveElNombreYLaAdvertencia() {
        Contribuyente c = service.parsearPersona(respuesta(
            "<errorConstancia><apellido>PEREZ</apellido><error>La CUIT fue cancelada</error>"
                + "<idPersona>20300238379</idPersona><nombre>JUAN</nombre></errorConstancia>"),
            CUIT);

        assertThat(c.nombre()).isEqualTo("PEREZ JUAN");
        assertThat(c.advertencia()).isEqualTo("La CUIT fue cancelada");
    }

    @Test
    void parsearPersona_noExiste_avisaQueNoEstaRegistrado() {
        String sinPersona = respuesta(
            "<errorConstancia><error>No existe persona con ese Id</error><idPersona>12345678901</idPersona></errorConstancia>");

        assertThatThrownBy(() -> service.parsearPersona(sinPersona, CUIT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no tiene registrado");
    }

    @Test
    void parsearPersona_soapFault_distingueInexistenteDeOtrosErrores() {
        String plantilla = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
            + "<soap:Fault><faultcode>soap:Server</faultcode><faultstring>%s</faultstring></soap:Fault>"
            + "</soap:Body></soap:Envelope>";

        assertThatThrownBy(() -> service.parsearPersona(plantilla.formatted("No existe persona con ese Id"), CUIT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parsearPersona(plantilla.formatted("Token vencido"), CUIT))
            .isInstanceOf(AfipIntegracionException.class)
            .hasMessageContaining("Token vencido");
    }
}
