package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Empleado;

/**
 * Flujo completo de caja (abrir/cerrar sesión, resumen) contra H2 real y
 * autenticación JWT real, mockeando solo el envío de emails.
 */
class CajaControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void abrirCaja_comoAdmin_creaSesionAbierta() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "montoInicial": 1000}""".formatted(admin.getIdEmpleado())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ABIERTA"))
            .andExpect(jsonPath("$.montoInicial").value(1000));
    }

    @Test
    void abrirCaja_comoVendedor_creaSesionAbierta() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "montoInicial": 1000}""".formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    void cerrarCaja_comoVendedor_laCierra() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "montoInicial": 1000}""".formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/caja/cerrar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CERRADA"));
    }

    @Test
    void abrirCaja_yaHaySesionAbiertaHoy_devuelve409() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");
        String body = """
            {"idEmpleado": %d, "montoInicial": 1000}""".formatted(admin.getIdEmpleado());

        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void cerrarCaja_conSesionAbierta_laCierra() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "montoInicial": 1000}""".formatted(admin.getIdEmpleado())))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/caja/cerrar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CERRADA"));
    }

    @Test
    void cerrarCaja_sinSesionAbierta_devuelve409() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        mockMvc.perform(post("/api/caja/cerrar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isConflict());
    }

    @Test
    void resumenDelDia_sinMovimientos_devuelveMontoInicialEnCero() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.montoInicial").value(0))
            .andExpect(jsonPath("$.cajaTotalDelDia").value(0));
    }

    /**
     * Decisión de negocio confirmada por el dueño del negocio: el VENDEDOR no puede ver el
     * historial de ventas bajo ninguna forma, ni siquiera el resumen/arqueo de su propio turno.
     * Único rol habilitado para estos endpoints: ADMIN.
     */
    @Test
    void resumenDelDia_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void resumenPorRango_comoVendedor_devuelve403_niSiquieraElDeSuPropioTurno() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        // El turno lo abre y cierra un ADMIN (abrir/cerrar caja también es exclusivo de ADMIN);
        // el vendedor solo vende dentro de ese turno, nunca administra ni consulta la caja.
        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"idEmpleado": %d, "montoInicial": 1000}""".formatted(admin.getIdEmpleado())))
            .andExpect(status().isOk());

        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");
        java.time.LocalDate hoy = java.time.LocalDate.now();

        mockMvc.perform(get("/api/caja/resumen")
                .param("desde", hoy.toString())
                .param("hasta", hoy.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
    }
}
