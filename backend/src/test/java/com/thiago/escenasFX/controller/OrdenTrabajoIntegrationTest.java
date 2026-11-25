package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Empleado;

/**
 * Orden de trabajo impresa (Etapa 1, Parte 5): el jefe la saca en PDF de 58mm para la Xprinter;
 * el técnico no tiene acceso a este endpoint (ve su trabajo en "Mis trabajos").
 */
class OrdenTrabajoIntegrationTest extends AbstractIntegrationTest {

    private Empleado admin;
    private Empleado fabian;
    private String tokenAdmin;
    private String tokenFabian;

    @BeforeEach
    void prepararDatos() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        fabian = crearEmpleado("fabian", "clave123", "TECNICO", null);
        tokenAdmin = login("admin1", "clave123");
        tokenFabian = login("fabian", "clave123");
    }

    private int crearTrabajo() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", fabian.getIdEmpleado());
        cuerpo.put("clienteNombre", "Juan Pérez & Hijos");
        cuerpo.put("clienteTelefono", "1155556666");
        cuerpo.put("direccionTrabajo", "Belgrano 233");
        cuerpo.put("descripcionTrabajo", "Instalar termotanque");
        cuerpo.put("observacionesTecnico", "Tocar timbre <2B>");
        cuerpo.put("horaDesde", "10:00");
        cuerpo.put("horaHasta", "12:00");
        cuerpo.put("estadoTrabajo", "AGENDADO");
        cuerpo.put("cerrar", false);

        String respuesta = mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta).get("idVenta").asInt();
    }

    @Test
    void orden_comoAdmin_devuelveUnPdf() throws Exception {
        int id = crearTrabajo();

        byte[] pdf = mockMvc.perform(get("/api/ventas/trabajo-domicilio/" + id + "/orden")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void orden_comoTecnico_devuelve403() throws Exception {
        int id = crearTrabajo();

        mockMvc.perform(get("/api/ventas/trabajo-domicilio/" + id + "/orden")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isForbidden());
    }

    @Test
    void orden_deUnTrabajoEliminado_devuelve409() throws Exception {
        int id = crearTrabajo();
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/anular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/ventas/trabajo-domicilio/" + id + "/orden")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isConflict());
    }
}
