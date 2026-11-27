package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.SuscripcionPush;
import com.thiago.escenasFX.repository.SuscripcionPushRepository;

class PushServiceTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

    private final SuscripcionPushRepository repo = mock(SuscripcionPushRepository.class);
    private final WebPushClient webPushClient = mock(WebPushClient.class);
    private ECPublicKey clavePublica;
    private MockRestServiceServer servidor;
    private PushService pushService;

    private static org.apache.http.HttpResponse respuestaConEstado(int codigo) {
        return new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), codigo, ""));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] enBytesFijos(BigInteger valor) {
        byte[] crudo = valor.toByteArray();
        byte[] fijo = new byte[32];
        int largo = Math.min(crudo.length, 32);
        System.arraycopy(crudo, crudo.length - largo, fijo, 32 - largo, largo);
        return fijo;
    }

    @BeforeEach
    void preparar() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = generador.generateKeyPair();
        clavePublica = (ECPublicKey) par.getPublic();
        String clavePrivada = base64Url(enBytesFijos(((ECPrivateKey) par.getPrivate()).getS()));

        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        pushService = new PushService(repo, builder.build(), "clave-publica", clavePrivada, "mailto:test@test.com",
            webPushClient);
    }

    private SuscripcionPush suscripcion() {
        SuscripcionPush s = new SuscripcionPush();
        s.setEndpoint(ENDPOINT);
        return s;
    }

    // Punto EC P-256 sin comprimir (65 bytes, prefijo 0x04) y secreto de 16 bytes, los dos
    // base64url — la forma real de PushSubscription.keys del navegador. Un valor inválido hace que
    // el constructor de Notification tire antes de llegar siquiera a webPushClient.enviar.
    private static final String P256DH_VALIDO = "BITb-u6qxWS2keaXnDzAoEEXZP5k1UJDy2yTE9lZGhhdvhwGZ2FtFs_cCQFsK75EqeRt8S8a2Uq6_IpcIaIqu_E";
    private static final String AUTH_VALIDO = "Q8IJA_aRbigEdNh-wezGQQ";

    private SuscripcionPush suscripcionConClaves() {
        SuscripcionPush s = suscripcion();
        s.setP256dh(P256DH_VALIDO);
        s.setAuth(AUTH_VALIDO);
        return s;
    }

    private Empleado empleado(int id) {
        Empleado e = new Empleado();
        e.setIdEmpleado(id);
        return e;
    }

    @Test
    void firmarJwt_produceUnaFirmaEs256VerificableYConAudienciaDelOrigen() throws Exception {
        String jwt = pushService.firmarJwt(ENDPOINT);

        String[] partes = jwt.split("\\.");
        assertThat(partes).hasSize(3);
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertThat(contenido).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("mailto:test@test.com");

        byte[] firma = Base64.getUrlDecoder().decode(partes[2]);
        assertThat(firma).hasSize(64);
        Signature verificador = Signature.getInstance("SHA256withECDSAinP1363Format");
        verificador.initVerify(clavePublica);
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(verificador.verify(firma)).isTrue();
    }

    @Test
    void sinClavesConfiguradas_noEnviaNadaYNoRompe() {
        PushService sinClaves = new PushService(repo, RestClient.create(), "", "", "mailto:test@test.com",
            webPushClient);

        sinClaves.avisarPeticionNuevaEnSegundoPlano();

        verify(repo, never()).findByEmpleado_RolAndEmpleado_ActivoTrue("ADMIN");
        assertThatThrownBy(sinClaves::clavePublica).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void guardarSuscripcion_rechazaUnEndpointQueNoSeaHttps() {
        assertThatThrownBy(() -> pushService.guardarSuscripcion("http://interno:8080/x", "p256dh", "auth", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void avisarPeticionNueva_haceElPostFirmadoSinContenido() {
        when(repo.findByEmpleado_RolAndEmpleado_ActivoTrue("ADMIN")).thenReturn(List.of(suscripcion()));
        servidor.expect(requestTo(ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("TTL", "3600"))
            .andExpect(header("Urgency", "high"))
            .andRespond(withSuccess());

        pushService.avisarPeticionNueva();

        servidor.verify();
    }

    @Test
    void avisarPeticionNueva_suscripcionVencida_laBorra() {
        SuscripcionPush vencida = suscripcion();
        when(repo.findByEmpleado_RolAndEmpleado_ActivoTrue("ADMIN")).thenReturn(List.of(vencida));
        servidor.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.GONE));

        pushService.avisarPeticionNueva();

        verify(repo).delete(vencida);
    }

    @Test
    void avisarPeticionNueva_errorDelServidorDePush_noPropagaNiBorra() {
        SuscripcionPush suscripcion = suscripcion();
        when(repo.findByEmpleado_RolAndEmpleado_ActivoTrue("ADMIN")).thenReturn(List.of(suscripcion));
        servidor.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        pushService.avisarPeticionNueva();

        verify(repo, never()).delete(suscripcion);
    }

    // --- Push con contenido ---

    @Test
    void avisarConContenido_sinClavesConfiguradas_noEnviaNada() throws Exception {
        PushService sinClaves = new PushService(repo, RestClient.create(), "", "", "mailto:test@test.com",
            webPushClient);

        sinClaves.avisarConContenido(empleado(1), "{}");

        verify(repo, never()).findByEmpleado_IdEmpleado(1);
        verify(webPushClient, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void avisarConContenido_suscripcionSinClavesDeCifrado_laSaltea() throws Exception {
        when(repo.findByEmpleado_IdEmpleado(1)).thenReturn(List.of(suscripcion()));

        pushService.avisarConContenido(empleado(1), "{\"tipo\":\"TRABAJO_NUEVO\"}");

        verify(webPushClient, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void avisarConContenido_envioExitoso_mandaElPayloadASuDispositivo() throws Exception {
        when(repo.findByEmpleado_IdEmpleado(1)).thenReturn(List.of(suscripcionConClaves()));
        when(webPushClient.enviar(org.mockito.ArgumentMatchers.any())).thenReturn(respuestaConEstado(201));

        pushService.avisarConContenido(empleado(1), "{\"tipo\":\"TRABAJO_NUEVO\"}");

        verify(webPushClient).enviar(org.mockito.ArgumentMatchers.any());
        verify(repo, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void avisarConContenido_salteaElDispositivoQueHizoElCambio_peroAvisaALosOtros() throws Exception {
        SuscripcionPush otroDispositivo = suscripcionConClaves();
        otroDispositivo.setEndpoint("https://fcm.googleapis.com/fcm/send/celular-del-jefe");
        when(repo.findByEmpleado_IdEmpleado(1)).thenReturn(List.of(suscripcionConClaves(), otroDispositivo));
        when(webPushClient.enviar(org.mockito.ArgumentMatchers.any())).thenReturn(respuestaConEstado(201));

        pushService.avisarConContenido(empleado(1), "{\"tipo\":\"CAMBIO_TRABAJO\"}", ENDPOINT);

        verify(webPushClient, org.mockito.Mockito.times(1)).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void avisarConContenido_suscripcionVencida_laBorra() throws Exception {
        SuscripcionPush vencida = suscripcionConClaves();
        when(repo.findByEmpleado_IdEmpleado(1)).thenReturn(List.of(vencida));
        when(webPushClient.enviar(org.mockito.ArgumentMatchers.any())).thenReturn(respuestaConEstado(410));

        pushService.avisarConContenido(empleado(1), "{}");

        verify(repo).delete(vencida);
    }

    @Test
    void avisarConContenido_elEnvioTiraExcepcion_noPropaga() throws Exception {
        when(repo.findByEmpleado_IdEmpleado(1)).thenReturn(List.of(suscripcionConClaves()));
        when(webPushClient.enviar(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("sin red"));

        pushService.avisarConContenido(empleado(1), "{}");
    }
}
