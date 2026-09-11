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

    /**
     * Regresión: antes no había ningún límite a la cantidad de intentos de usuario/contraseña que
     * se podían probar contra /api/auth/login (fuerza bruta / credential stuffing sin freno).
     * Usuario propio ("bloqueoTest") para no interferir con el contador de otros tests: es un
     * contador en memoria compartido por todo el proceso, no se resetea entre @Test.
     */
    @Test
    void login_superaMaximoDeIntentosFallidos_bloqueaAunConCredencialesCorrectas() throws Exception {
        crearEmpleado("bloqueoTest", "clave123", "ADMIN", "bloqueotest@test.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"usuario":"bloqueoTest","password":"incorrecta"}"""))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"usuario":"bloqueoTest","password":"clave123"}"""))
            .andExpect(status().isUnauthorized());
    }
}
