package com.thiago.escenasFX.service;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.thiago.escenasFX.exception.EmailEnvioException;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

/**
 * Envío de emails vía la API HTTPS de Resend (https://resend.com), no SMTP directo: Railway
 * bloquea el puerto 587 saliente en los planes Free/Trial/Hobby (política antispam de la
 * plataforma), así que un JavaMailSender tradicional nunca conecta desde ahí. Requiere un
 * dominio propio verificado en Resend para poder mandarle a cualquier destinatario — con el
 * remitente de prueba (onboarding@resend.dev) solo se puede mandar a la casilla del dueño de la
 * cuenta de Resend.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final EmpleadoRepository empleadoRepo;
    private final String apiKey;
    private final String remitente;

    public EmailService(RestClient.Builder builder, EmpleadoRepository empleadoRepo,
            @Value("${resend.api-key}") String apiKey, @Value("${resend.remitente}") String remitente) {
        this.restClient = builder.build();
        this.empleadoRepo = empleadoRepo;
        this.apiKey = apiKey;
        this.remitente = remitente;
    }

    /**
     * Usado por NotificacionService (alertas de stock bajo): todos los ADMIN con email configurado.
     */
    public void enviarAAdmins(String asunto, String cuerpo) {
        List<String> emails = emailsDeAdmins();
        if (emails.isEmpty()) {
            throw new IllegalStateException("No hay ningún ADMIN con email configurado");
        }

        enviar(emails, asunto, cuerpo, null, null);
    }

    private List<String> emailsDeAdmins() {
        return empleadoRepo.findByRol("ADMIN").stream()
            .map(Empleado::getEmail)
            .filter(Objects::nonNull)
            .filter(email -> !email.isBlank())
            .toList();
    }

    /**
     * Usado por Presupuestos y por el comprobante de venta: el membrete (logo, dirección,
     * teléfono) vive en el PDF adjunto, no en el cuerpo del mail — que queda un texto simple.
     */
    public void enviarConAdjuntoPdf(String destinatario, String asunto, String cuerpoTexto, String nombreArchivo,
            byte[] pdfBytes) {
        enviar(List.of(destinatario), asunto, cuerpoTexto, nombreArchivo, pdfBytes);
    }

    private void enviar(List<String> destinatarios, String asunto, String cuerpo, String nombreArchivo,
            byte[] adjuntoPdf) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", remitente);
        body.put("to", destinatarios);
        body.put("subject", asunto);
        body.put("text", cuerpo);
        if (adjuntoPdf != null) {
            body.put("attachments", List.of(Map.of(
                "filename", nombreArchivo,
                "content", Base64.getEncoder().encodeToString(adjuntoPdf))));
        }

        // Se loguea el resultado de cada envío (la respuesta de Resend trae el id del email, que se
        // puede buscar en su panel para ver si se entregó o rebotó): un 2xx solo significa que Resend
        // lo aceptó, y sin este rastro un mail que no llega no se podía diagnosticar desde el backend.
        try {
            String respuesta = restClient.post()
                .uri(RESEND_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            log.info("Email aceptado por Resend: asunto=\"{}\", destinatarios={}, respuesta={}", asunto,
                destinatarios.size(), respuesta);
        } catch (Exception e) {
            log.error("Resend rechazó el email con asunto \"{}\": {}", asunto, e.getMessage());
            throw new EmailEnvioException("No se pudo enviar el email vía Resend: " + e.getMessage(), e);
        }
    }
}
