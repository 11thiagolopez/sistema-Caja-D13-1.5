package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.thiago.escenasFX.model.Empleado;

/**
 * El jefe y el empleado del local comparten el usuario ADMIN: lo que uno hace en un trabajo le tiene
 * que llegar al otro como aviso (campanita + push), con qué cambió, para no avisarse por WhatsApp.
 */
class AvisosAlJefeIntegrationTest extends AbstractIntegrationTest {

    private Empleado admin;
    private Empleado fabian;
    private Empleado juan;
    private String tokenAdmin;

    @BeforeEach
    void prepararDatos() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        fabian = crearEmpleado("fabian", "clave123", "TECNICO", null);
        fabian.setNombre("Fabián");
        empleadoRepo.save(fabian);
        juan = crearEmpleado("juan", "clave123", "TECNICO", null);
        juan.setNombre("Juan");
        empleadoRepo.save(juan);
        tokenAdmin = login("admin1", "clave123");
    }

    private Map<String, Object> cuerpo(Integer idVenta, Empleado tecnico, String fecha) {
        Map<String, Object> cuerpo = new HashMap<>();
        if (idVenta != null) {
            cuerpo.put("idVenta", idVenta);
        }
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        cuerpo.put("clienteNombre", "Carlos Ferreyra");
        cuerpo.put("direccionTrabajo", "Soldados 750");
        cuerpo.put("fechaProgramada", fecha);
        cuerpo.put("horaDesde", "10:00");
        cuerpo.put("estadoTrabajo", "AGENDADO");
        cuerpo.put("cerrar", false);
        return cuerpo;
    }

    private int guardar(Map<String, Object> cuerpo) throws Exception {
        String r = mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(r).get("idVenta").asInt();
    }

    private List<String> avisosDelJefe() throws Exception {
        String r = mockMvc.perform(get("/api/notificaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> textos = new ArrayList<>();
        for (JsonNode n : objectMapper.readTree(r)) {
            if ("CAMBIO_TRABAJO".equals(n.get("tipo").asText())) {
                textos.add(n.get("texto").asText());
            }
        }
        return textos;
    }

    @Test
    void crearUnTrabajo_avisaAlJefeConLosDatos() throws Exception {
        int id = guardar(cuerpo(null, fabian, "2026-11-28"));

        assertThat(avisosDelJefe()).singleElement().asString()
            .startsWith("Nuevo trabajo #" + id + " (Soldados 750)")
            .contains("28/11").contains("10:00").contains("Técnico: Fabián");
    }

    @Test
    void cambiarFechaYTecnico_avisaQueCambio_yGuardarSinCambiosNoAvisa() throws Exception {
        int id = guardar(cuerpo(null, fabian, "2026-11-28"));

        guardar(cuerpo(id, juan, "2026-11-29"));
        List<String> avisos = avisosDelJefe();
        assertThat(avisos).hasSize(2);
        assertThat(avisos).anySatisfy(texto -> assertThat(texto)
            .startsWith("Cambios en #" + id + " (Soldados 750)")
            .contains("fecha").contains("28/11").contains("29/11")
            .contains("técnico Fabián → Juan"));

        guardar(cuerpo(id, juan, "2026-11-29"));
        assertThat(avisosDelJefe()).hasSize(2);
    }
}
