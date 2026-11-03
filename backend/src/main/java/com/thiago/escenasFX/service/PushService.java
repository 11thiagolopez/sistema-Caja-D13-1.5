package com.thiago.escenasFX.service;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.SuscripcionPush;
import com.thiago.escenasFX.repository.SuscripcionPushRepository;

/**
 * Avisa a los dispositivos de los ADMIN que hay una petición nueva, con Web Push estándar (la
 * notificación de Android llega aunque el sistema esté cerrado). El push va SIN contenido, solo
 * firmado con VAPID: así no hace falta cifrar nada ni sumar una librería, y ni montos ni motivos
 * pasan por los servidores de Google. El texto fijo lo arma public/sw.js en el dispositivo.
 *
 * Es best-effort, igual que las alertas de stock: si falla, la petición queda igual en la pantalla
 * de Peticiones. Sin claves VAPID configuradas el servicio queda desactivado, sin romper nada.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private static final String ROL_ADMIN = "ADMIN";
    private static final long VIGENCIA_JWT_SEGUNDOS = 12 * 60 * 60;
    private static final int TIEMPO_DE_VIDA_SEGUNDOS = 60 * 60;
    private static final int TIMEOUT_MS = 5000;

    private final SuscripcionPushRepository suscripcionRepo;
    private final RestClient restClient;
    private final String clavePublica;
    private final String clavePrivada;
    private final String contacto;

    @Autowired
    public PushService(SuscripcionPushRepository suscripcionRepo, RestClient.Builder builder,
            @Value("${push.vapid.public-key:}") String clavePublica,
            @Value("${push.vapid.private-key:}") String clavePrivada,
            @Value("${push.vapid.contacto:mailto:admin@example.com}") String contacto) {
        this(suscripcionRepo, builder.requestFactory(conTimeouts()).build(), clavePublica, clavePrivada, contacto);
    }

    PushService(SuscripcionPushRepository suscripcionRepo, RestClient restClient, String clavePublica,
            String clavePrivada, String contacto) {
        this.suscripcionRepo = suscripcionRepo;
        this.restClient = restClient;
        this.clavePublica = clavePublica;
        this.clavePrivada = clavePrivada;
        this.contacto = contacto;
    }

    private static SimpleClientHttpRequestFactory conTimeouts() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        return requestFactory;
    }

    private boolean configurado() {
        return !clavePublica.isBlank() && !clavePrivada.isBlank();
    }

    public String clavePublica() {
        if (!configurado()) {
            throw new IllegalStateException("Las notificaciones todavía no están configuradas en el servidor");
        }
        return clavePublica;
    }

    /** Un mismo dispositivo puede volver a registrarse: el endpoint queda a nombre del último que entró. */
    @Transactional
    public void guardarSuscripcion(String endpoint, Empleado empleado) {
        if (!endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("La suscripción de notificaciones no es válida");
        }
        SuscripcionPush suscripcion = suscripcionRepo.findByEndpoint(endpoint).orElseGet(SuscripcionPush::new);
        suscripcion.setEndpoint(endpoint);
        suscripcion.setEmpleado(empleado);
        suscripcionRepo.save(suscripcion);
    }

    /**
     * En segundo plano y llamado desde el controller, ya con la transacción confirmada: el vendedor
     * no tiene que esperar a los servidores de push, y el aviso nunca llega antes que la petición.
     */
    public void avisarPeticionNuevaEnSegundoPlano() {
        if (configurado()) {
            CompletableFuture.runAsync(this::avisarPeticionNueva);
        }
    }

    void avisarPeticionNueva() {
        try {
            List<SuscripcionPush> suscripciones = suscripcionRepo.findByEmpleado_RolAndEmpleado_ActivoTrue(ROL_ADMIN);
            suscripciones.forEach(this::enviar);
        } catch (RuntimeException e) {
            log.error("No se pudieron enviar los avisos de petición nueva: {}", e.getMessage(), e);
        }
    }

    private void enviar(SuscripcionPush suscripcion) {
        try {
            restClient.post()
                .uri(URI.create(suscripcion.getEndpoint()))
                .header("Authorization", "vapid t=" + firmarJwt(suscripcion.getEndpoint()) + ", k=" + clavePublica)
                .header("TTL", String.valueOf(TIEMPO_DE_VIDA_SEGUNDOS))
                .header("Urgency", "high")
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone e) {
            // El navegador dio de baja la suscripción (desinstaló, sacó el permiso): no va a volver.
            suscripcionRepo.delete(suscripcion);
        } catch (RuntimeException | GeneralSecurityException e) {
            log.error("No se pudo enviar un aviso de petición nueva: {}", e.getMessage());
        }
    }

    /** JWT de VAPID (RFC 8292), firmado con ES256 y con la audiencia en el origen del endpoint. */
    String firmarJwt(String endpoint) throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        String audiencia = uri.getScheme() + "://" + uri.getAuthority();
        long vence = Instant.now().getEpochSecond() + VIGENCIA_JWT_SEGUNDOS;

        String contenido = base64Url("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8)) + "."
            + base64Url("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}".formatted(audiencia, vence, contacto)
                .getBytes(StandardCharsets.UTF_8));

        // "P1363" da r||s de largo fijo, que es lo que pide JWT (el formato por defecto de Java es DER).
        Signature firma = Signature.getInstance("SHA256withECDSAinP1363Format");
        firma.initSign(clavePrivadaEc());
        firma.update(contenido.getBytes(StandardCharsets.UTF_8));
        return contenido + "." + base64Url(firma.sign());
    }

    private ECPrivateKey clavePrivadaEc() throws GeneralSecurityException {
        AlgorithmParameters parametros = AlgorithmParameters.getInstance("EC");
        parametros.init(new ECGenParameterSpec("secp256r1"));
        BigInteger d = new BigInteger(1, Base64.getUrlDecoder().decode(clavePrivada));
        return (ECPrivateKey) KeyFactory.getInstance("EC")
            .generatePrivate(new ECPrivateKeySpec(d, parametros.getParameterSpec(ECParameterSpec.class)));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
