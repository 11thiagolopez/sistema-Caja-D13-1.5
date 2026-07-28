package com.thiago.escenasFX.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.LoginRequest;
import com.thiago.escenasFX.dto.LoginResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.security.JwtService;
import com.thiago.escenasFX.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Empleado empleado = authService.login(request.getUsuario(), request.getPassword());
        String token = jwtService.generarToken(empleado);
        return new LoginResponse(empleado.getIdEmpleado(), empleado.getNombre(), empleado.getUsuario(),
            empleado.getRol(), token);
    }
}
