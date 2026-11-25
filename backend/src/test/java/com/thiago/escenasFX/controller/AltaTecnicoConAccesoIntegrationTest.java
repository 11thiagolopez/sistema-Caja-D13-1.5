package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * El camino completo que hace el dueño desde la pantalla de Técnicos: dar de alta un técnico con
 * usuario y contraseña, y que ese técnico pueda entrar y ver solo "Mis trabajos" — nada de caja,
 * productos ni la agenda del jefe.
 */
class AltaTecnicoConAccesoIntegrationTest extends AbstractIntegrationTest {

    private String tokenAdmin;

    @BeforeEach
    void prepararDatos() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        tokenAdmin = login("admin1", "clave123");
    }

    @Test
    void tecnicoDadoDeAltaConUsuario_entraYVeSoloSusTrabajos() throws Exception {
        mockMvc.perform(post("/api/empleados/tecnicos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"nombre\": \"Fabián\", \"comision\": 50, \"usuario\": \"fabian\", \"password\": \"clave123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.usuario").value("fabian"))
            .andExpect(jsonPath("$.rol").value("TECNICO"));

        String tokenFabian = login("fabian", "clave123");

        mockMvc.perform(get("/api/mis-trabajos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/notificaciones/no-leidas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/trabajo-domicilio?desde=2026-01-01&hasta=2026-12-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isForbidden());
    }

    @Test
    void altaConUsuarioSinContrasena_rechazaCon400() throws Exception {
        mockMvc.perform(post("/api/empleados/tecnicos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"nombre\": \"Fabián\", \"comision\": 50, \"usuario\": \"fabian\"}"))
            .andExpect(status().isBadRequest());
    }
}
