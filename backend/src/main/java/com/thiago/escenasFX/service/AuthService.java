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
    private final LoginAttemptService loginAttemptService;

    public AuthService(EmpleadoRepository empleadoRepo, PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService) {
        this.empleadoRepo = empleadoRepo;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    public Empleado login(String usuario, String passwordPlano) {
        loginAttemptService.verificarNoBloqueado(usuario);

        Empleado empleado = empleadoRepo.findByUsuario(usuario).orElse(null);

        // Mismo motivo en los tres casos para no distinguir el mensaje (usuario inexistente,
        // contraseña incorrecta, usuario dado de baja): no dar pistas de qué usuarios existen.
        if (empleado == null || !passwordEncoder.matches(passwordPlano, empleado.getPasswordHash())
                || !empleado.isActivo()) {
            loginAttemptService.registrarFallo(usuario);
            throw new AuthenticationFailedException("Usuario o contraseña inválidos");
        }

        loginAttemptService.registrarExito(usuario);
        return empleado;
    }
}
