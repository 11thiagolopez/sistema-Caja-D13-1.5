package com.thiago.escenasFX.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.thiago.escenasFX.exception.AuthenticationFailedException;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

@Service
public class AuthService {

    private final EmpleadoRepository empleadoRepo;
    private final PasswordEncoder passwordEncoder;

    public AuthService(EmpleadoRepository empleadoRepo, PasswordEncoder passwordEncoder) {
        this.empleadoRepo = empleadoRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public Empleado login(String usuario, String passwordPlano) {
        Empleado empleado = empleadoRepo.findByUsuario(usuario)
            .orElseThrow(() -> new AuthenticationFailedException("Usuario o contraseña inválidos"));

        if (!passwordEncoder.matches(passwordPlano, empleado.getPasswordHash())) {
            throw new AuthenticationFailedException("Usuario o contraseña inválidos");
        }

        // No se distingue el mensaje de "usuario dado de baja" del de credenciales inválidas, por
        // el mismo motivo que ya se documenta arriba: no dar pistas de qué usuarios existen.
        if (!empleado.isActivo()) {
            throw new AuthenticationFailedException("Usuario o contraseña inválidos");
        }

        return empleado;
    }
}
