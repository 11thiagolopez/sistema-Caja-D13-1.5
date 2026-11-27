package com.thiago.escenasFX.service;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.http.HttpResponse;
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

import nl.martijndwars.webpush.Notification;

/**
 * Dos formas de avisar, ambas Web Push estándar (la notificación llega aunque el sistema esté
 * cerrado):
 * - {@link #avisarPeticionNuevaEnSegundoPlano()}: sin contenido, solo firmado con VAPID a mano
 *   (RFC 8292) — ni montos ni motivos pasan por los servidores de Google. El texto fijo lo arma
 *   public/sw.js en el dispositivo. La usan las Peticiones (Autorizaciones).
 * - {@link #avisarConContenido}/{@link #avisarConContenidoATodosLosAdmin}: con contenido cifrado
 *   (RFC 8291, AES128GCM) vía la librería nl.martijndwars:web-push — cada aviso tiene su propio
 *   texto y abre un trabajo puntual. La usan los avisos de trabajos a domicilio. Necesita que la
 *   suscripción tenga p256dh/auth (las de antes de que existiera esto no los tienen, quedan
 *   activas solo para el aviso sin contenido hasta que ese dispositivo se vuelva a suscribir).
 *
 * Las dos son best-effort: si fallan, lo que las disparó se completa igual. Sin claves VAPID
 * configuradas, el servicio entero queda desactivado, sin romper nada.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private static final String ROL_ADMIN = "ADMIN";
    private static final long VIGENCIA_JWT_SEGUNDOS = 12 * 60 * 60;
    private static final int TIEMPO_DE_VIDA_SEGUNDOS = 60 * 60;
    private static final int TIMEOUT_MS = 5000;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_GONE = 410;

    // La librería nl.martijndwars:web-push necesita el proveedor "BC" (BouncyCastle) registrado
    // para leer las claves EC de cada suscripción (p256dh/auth) — AfipAuthService ya lo registra,
    // pero no hay que depender del orden en que Spring cargue las clases. Security.addProvider no
    // rompe si ya estaba registrado (devuelve -1, no tira).
    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    private final SuscripcionPushRepository suscripcionRepo;
    private final RestClient restClient;
    private final String clavePublica;
    private final String clavePrivada;
    private final String contacto;
    private final WebPushClient webPushClient;

    @Autowired
    public PushService(SuscripcionPushRepository suscripcionRepo, RestClient.Builder builder,
            @Value("${push.vapid.public-key:}") String clavePublica,
            @Value("${push.vapid.private-key:}") String clavePrivada,
            @Value("${push.vapid.contacto:mailto:admin@example.com}") String contacto) {
        this(suscripcionRepo, builder.requestFactory(conTimeouts()).build(), clavePublica, clavePrivada, contacto,
            notification -> new nl.martijndwars.webpush.PushService(clavePublica, clavePrivada, contacto)
                .send(notification));
    }

    PushService(SuscripcionPushRepository suscripcionRepo, RestClient restClient, String clavePublica,
            String clavePrivada, String contacto, WebPushClient webPushClient) {
        this.suscripcionRepo = suscripcionRepo;
        this.restClient = restClient;
        this.clavePublica = clavePublica;
        this.clavePrivada = clavePrivada;
        this.contacto = contacto;
        this.webPushClient = webPushClient;
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
    public void guardarSuscripcion(String endpoint, String p256dh, String auth, Empleado empleado) {
        if (!endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("La suscripción de notificaciones no es válida");
        }
        SuscripcionPush suscripcion = suscripcionRepo.findByEndpoint(endpoint).orElseGet(SuscripcionPush::new);
        suscripcion.setEndpoint(endpoint);
        suscripcion.setP256dh(p256dh);
        suscripcion.setAuth(auth);
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

    // --- Push con contenido (trabajos a domicilio) ---

    /** Avisa a un empleado puntual (ej. Fabián) en todos sus dispositivos suscriptos. */
    public void avisarConContenido(Empleado destinatario, String payloadJson) {
        avisarConContenido(destinatario, payloadJson, null);
    }

    /**
     * Igual, salteando el dispositivo cuyo endpoint es {@code endpointExcluido} (el que hizo el
     * cambio: no tiene sentido avisarle a uno mismo). Importa porque el jefe y el empleado del local
     * comparten el mismo usuario ADMIN: lo que carga uno le tiene que llegar al otro, no a él.
     */
    public void avisarConContenido(Empleado destinatario, String payloadJson, String endpointExcluido) {
        if (!configurado()) {
            return;
        }
        suscripcionRepo.findByEmpleado_IdEmpleado(destinatario.getIdEmpleado()).stream()
            .filter(s -> endpointExcluido == null || !endpointExcluido.equals(s.getEndpoint()))
            .forEach(s -> enviarConContenido(s, payloadJson));
    }

    private void enviarConContenido(SuscripcionPush suscripcion, String payloadJson) {
        if (suscripcion.getP256dh() == null || suscripcion.getAuth() == null) {
            // Suscripción de antes de que existiera el push con contenido: no se puede cifrar para
            // ella todavía. No es un error — el dispositivo se va a poner al día solo cuando
            // vuelva a activar las notificaciones.
            return;
        }
        try {
            Notification notification = new Notification(suscripcion.getEndpoint(), suscripcion.getP256dh(),
                suscripcion.getAuth(), payloadJson);
            HttpResponse respuesta = webPushClient.enviar(notification);
            int status = respuesta.getStatusLine().getStatusCode();
            if (status == HTTP_NOT_FOUND || status == HTTP_GONE) {
                suscripcionRepo.delete(suscripcion);
            } else if (status >= 300) {
                log.error("Push con contenido rechazado (HTTP {}) para la suscripción #{}", status,
                    suscripcion.getIdSuscripcion());
            }
        } catch (Exception e) {
            log.error("No se pudo enviar un push con contenido: {}", e.getMessage(), e);
        }
    }
}
