package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

    @Test
    void listarPresupuestos_comoVendedorOAdmin_permiteAmbosRoles() throws Exception {
        // A diferencia de /api/reportes (exclusivo ADMIN), Presupuestos es una herramienta de
        // venta del día a día como Cobros — ambos roles generan y consultan.
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/presupuestos")
                .param("desde", "2026-01-01").param("hasta", "2026-01-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isOk());

        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(get("/api/presupuestos")
                .param("desde", "2026-01-01").param("hasta", "2026-01-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk());
    }

    @Test
    void enviarComprobanteDeVenta_comoVendedor_noDevuelve403() throws Exception {
        // Día a día como registrar la venta — solo el historial (GET /api/ventas) es ADMIN-only.
        // La venta #1 no existe en este test, así que el endpoint es alcanzable (no 403) pero
        // responde 400 por regla de negocio, no por permisos.
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/ventas/1/enviar-comprobante")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@test.com\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verVentaPuntual_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/ventas/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void trabajoADomicilio_comoVendedor_devuelve403() throws Exception {
        // Todo el módulo (a diferencia de Presupuestos) queda exclusivo de ADMIN.
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEmpleado\":1,\"clienteNombre\":\"Cliente\",\"cerrar\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void trabajoADomicilio_comoAdmin_noDevuelve403() throws Exception {
        var admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEmpleado\":" + admin.getIdEmpleado() + ",\"clienteNombre\":\"Cliente\",\"cerrar\":false}"))
            .andExpect(status().isOk());
    }

    @Test
    void cotizacionActual_comoVendedorOAdmin_permiteAmbosRoles() throws Exception {
        // Gate diario de cotización: cualquier rol puede consultarla apenas loguea (no la
        // dispara, solo pregunta si ya existe).
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/cotizacion/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isNoContent());

        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(get("/api/cotizacion/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isNoContent());
    }

    @Test
    void cargarCotizacion_comoVendedor_noDevuelve403() throws Exception {
        // La carga automática (misma API que ya está mockeada en AbstractIntegrationTest) la
        // puede disparar cualquiera de los dos roles.
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/cotizacion/cargar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valorVenta").value(1000));
    }

    @Test
    void cargarCotizacionManual_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/cotizacion/manual")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorVenta\": 1300}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void cargarCotizacionManual_comoAdmin_noDevuelve403() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        mockMvc.perform(post("/api/cotizacion/manual")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorVenta\": 1300}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manual").value(true));
    }
}
