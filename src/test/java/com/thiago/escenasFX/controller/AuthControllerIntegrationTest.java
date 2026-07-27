package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Login real de punta a punta: Empleado en H2 -> AuthService (BCrypt) -> JwtService -> token
 * devuelto por AuthController. El resto de los tests de integración reutilizan este mismo flujo
 * (ver AbstractIntegrationTest.login) para autenticar sus propias requests.
 */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void login_credencialesValidas_devuelveTokenYDatosDelEmpleado() throws Exception {
        crearEmpleado("aleja", "clave123", "ADMIN", "aleja@test.com");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"usuario":"aleja","password":"clave123"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usuario").value("aleja"))
            .andExpect(jsonPath("$.rol").value("ADMIN"))
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_passwordIncorrecta_devuelve401() throws Exception {
        crearEmpleado("aleja", "clave123", "ADMIN", "aleja@test.com");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"usuario":"aleja","password":"incorrecta"}"""))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void login_usuarioInexistente_devuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"usuario":"no-existe","password":"clave123"}"""))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_sinPassword_devuelve400PorValidacion() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"usuario":"aleja"}"""))
            .andExpect(status().isBadRequest());
    }
}
