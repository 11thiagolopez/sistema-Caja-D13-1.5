package com.thiago.escenasFX.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    public static final int VIGENCIA_MINUTOS = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordEncoder passwordEncoder;

    public OtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String generarCodigo() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public String hash(String codigo) {
        return passwordEncoder.encode(codigo);
    }

    public boolean coincide(String codigoIngresado, String hash) {
        return passwordEncoder.matches(codigoIngresado, hash);
    }

    public LocalDateTime nuevaExpiracion() {
        return LocalDateTime.now().plusMinutes(VIGENCIA_MINUTOS);
    }
}
