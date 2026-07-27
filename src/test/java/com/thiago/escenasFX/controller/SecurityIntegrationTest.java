package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Verifica las reglas por rol de SecurityConfig contra el filtro JWT real (no @WithMockUser):
 * sin token debe ser 401, con token de un rol sin permiso debe ser 403.
 */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    void endpointProtegido_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/ventas").param("desde", "2026-01-01").param("hasta", "2026-01-31"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_conTokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(get("/api/ventas")
                .param("desde", "2026-01-01").param("hasta", "2026-01-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void verVentas_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/ventas")
                .param("desde", "2026-01-01").param("hasta", "2026-01-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void verReportes_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/reportes/balance")
                .param("desde", "2026-01-01").param("hasta", "2026-01-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void listarProductos_comoVendedorOAdmin_permiteAmbosRoles() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isOk());

        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk());
    }
}
