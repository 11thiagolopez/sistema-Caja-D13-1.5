package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Empleado;

/**
 * Flujo completo de caja (abrir/cerrar sesión, resumen, retiro con OTP) contra H2 real y
 * autenticación JWT real, mockeando solo el envío de emails.
 */
class CajaControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern PATRON_CODIGO = Pattern.compile("Código de confirmación: (\\d{6})");

    private String extraerCodigoOtpDelUltimoEmail() {
        ArgumentCaptor<String> cuerpoCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarOtpAAdmins(anyString(), cuerpoCaptor.capture());
        Matcher m = PATRON_CODIGO.matcher(cuerpoCaptor.getValue());
        assertThat(m.find()).as("el email debe contener el código OTP de 6 dígitos").isTrue();
        return m.group(1);
    }

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

    @Test
    void retiro_flujoCompletoConOtpDelEmail_creaMovimientoCaja() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        String solicitarBody = """
            {"idEmpleado": %d, "monto": 300, "motivo": "Gastos varios", "medioPago": "EFECTIVO"}
            """.formatted(admin.getIdEmpleado());

        String respuesta = mockMvc.perform(post("/api/caja/retiro/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(solicitarBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andReturn().getResponse().getContentAsString();

        Integer idSolicitud = objectMapper.readTree(respuesta).get("idSolicitud").asInt();
        String codigo = extraerCodigoOtpDelUltimoEmail();

        mockMvc.perform(post("/api/caja/retiro/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idSolicitud": %d, "codigo": "%s"}""".formatted(idSolicitud, codigo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipo").value("RETIRO"))
            .andExpect(jsonPath("$.monto").value(300));
    }

    @Test
    void confirmarRetiro_codigoIncorrecto_devuelve401() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        String solicitarBody = """
            {"idEmpleado": %d, "monto": 300, "motivo": "Gastos varios", "medioPago": "EFECTIVO"}
            """.formatted(admin.getIdEmpleado());

        String respuesta = mockMvc.perform(post("/api/caja/retiro/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(solicitarBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Integer idSolicitud = objectMapper.readTree(respuesta).get("idSolicitud").asInt();

        mockMvc.perform(post("/api/caja/retiro/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idSolicitud": %d, "codigo": "000000"}""".formatted(idSolicitud)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void solicitarRetiro_comoVendedor_generaSolicitudPendiente() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com"); // recibe el email de OTP
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/caja/retiro/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "monto": 300, "motivo": "Gastos", "medioPago": "EFECTIVO"}"""
                    .formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    /**
     * Flujo real: el VENDEDOR pide el retiro, el OTP le llega por email al ADMIN (nunca al
     * VENDEDOR), pero es el VENDEDOR quien está frente a la caja y termina la operación una vez
     * que el ADMIN le pasa el código (llamado, WhatsApp, etc.). El control de seguridad está en
     * quién recibe el email, no en qué rol aprieta "Confirmar" — por eso VENDEDOR puede confirmar
     * su propia solicitud si tiene el código correcto.
     */
    @Test
    void confirmarRetiro_comoVendedor_funciona() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com"); // recibe el email de OTP
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        String respuesta = mockMvc.perform(post("/api/caja/retiro/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "monto": 300, "motivo": "Gastos", "medioPago": "EFECTIVO"}"""
                    .formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Integer idSolicitud = objectMapper.readTree(respuesta).get("idSolicitud").asInt();
        String codigo = extraerCodigoOtpDelUltimoEmail();

        mockMvc.perform(post("/api/caja/retiro/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idSolicitud": %d, "codigo": "%s"}""".formatted(idSolicitud, codigo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipo").value("RETIRO"));
    }

    /**
     * El caso de uso real: el VENDEDOR pide el retiro, el OTP le llega por email a un ADMIN que
     * no fue quien lo solicitó, y ese ADMIN lo confirma con el idSolicitud (que ya viene en el
     * asunto del email) + el código.
     */
    @Test
    void retiro_solicitadoPorVendedor_loConfirmaUnAdminDistinto() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        String respuesta = mockMvc.perform(post("/api/caja/retiro/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content("""
                    {"idEmpleado": %d, "monto": 300, "motivo": "Gastos varios", "medioPago": "EFECTIVO"}"""
                    .formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Integer idSolicitud = objectMapper.readTree(respuesta).get("idSolicitud").asInt();
        String codigo = extraerCodigoOtpDelUltimoEmail();

        mockMvc.perform(post("/api/caja/retiro/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"idSolicitud": %d, "codigo": "%s"}""".formatted(idSolicitud, codigo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipo").value("RETIRO"));
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
