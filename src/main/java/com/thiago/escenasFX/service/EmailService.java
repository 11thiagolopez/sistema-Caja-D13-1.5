package com.thiago.escenasFX.service;

import java.util.List;
import java.util.Objects;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

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
}
