package com.thiago.escenasFX.service;

import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmpleadoRepository empleadoRepo;

    public EmailService(JavaMailSender mailSender, EmpleadoRepository empleadoRepo) {
        this.mailSender = mailSender;
        this.empleadoRepo = empleadoRepo;
    }

    public void enviarOtpAAdmins(String asunto, String cuerpo) {
        List<String> emails = empleadoRepo.findByRol("ADMIN").stream()
            .map(Empleado::getEmail)
            .filter(Objects::nonNull)
            .filter(email -> !email.isBlank())
            .toList();

        if (emails.isEmpty()) {
            throw new IllegalStateException(
                "No hay ningún ADMIN con email configurado para recibir el código OTP");
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(emails.toArray(new String[0]));
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        mailSender.send(mensaje);
    }

    /**
     * Usado por Presupuestos y por el comprobante de venta: el membrete (logo, dirección,
     * teléfono) vive en el PDF adjunto, no en el cuerpo del mail — que queda un texto simple.
     */
    public void enviarConAdjuntoPdf(String destinatario, String asunto, String cuerpoTexto, String nombreArchivo,
            byte[] pdfBytes) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoTexto);
            helper.addAttachment(nombreArchivo, new ByteArrayResource(pdfBytes), "application/pdf");
            mailSender.send(mensaje);
        } catch (MessagingException e) {
            throw new IllegalStateException("No se pudo armar el email con el PDF adjunto", e);
        }
    }
}
