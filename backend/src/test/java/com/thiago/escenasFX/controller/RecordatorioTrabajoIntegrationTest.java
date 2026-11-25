package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.service.RecordatorioTrabajoService;

/** Recordatorio de 1h antes: una sola vez por trabajo, y de nuevo si se reprograma. */
class RecordatorioTrabajoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RecordatorioTrabajoService recordatorioService;

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

    private Map<String, Object> trabajo(String horaDesde) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", fabian.getIdEmpleado());
        cuerpo.put("clienteNombre", "Carlos Ferreyra");
        cuerpo.put("direccionTrabajo", "Soldados 750");
        cuerpo.put("fechaProgramada", "2030-03-10");
        cuerpo.put("horaDesde", horaDesde);
        cuerpo.put("estadoTrabajo", "AGENDADO");
        cuerpo.put("cerrar", false);
        return cuerpo;
    }

    private int crearTrabajo(String horaDesde) throws Exception {
        String r = mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(objectMapper.writeValueAsString(trabajo(horaDesde))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(r).get("idVenta").asInt();
    }

    private JsonNode avisosDeFabian() throws Exception {
        String r = mockMvc.perform(get("/api/notificaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(r);
    }

    private long recordatorios() throws Exception {
        long n = 0;
        for (JsonNode aviso : avisosDeFabian()) {
            if ("RECORDATORIO".equals(aviso.get("tipo").asText())) {
                n++;
            }
        }
        return n;
    }

    @Test
    void avisaUnaSolaVezDentroDeLaHoraPrevia() throws Exception {
        crearTrabajo("10:00");

        assertThat(recordatorioService.enviarPendientes(LocalDateTime.of(2030, 3, 10, 8, 30))).isZero();
        assertThat(recordatorioService.enviarPendientes(LocalDateTime.of(2030, 3, 10, 9, 5))).isEqualTo(1);
        assertThat(recordatorioService.enviarPendientes(LocalDateTime.of(2030, 3, 10, 9, 10))).isZero();

        assertThat(recordatorios()).isEqualTo(1);
        assertThat(avisosDeFabian().toString()).contains("A las 10:00 tenés un trabajo en Soldados 750");
    }

    @Test
    void unTrabajoQueYaEmpezo_noRecibeAviso() throws Exception {
        crearTrabajo("10:00");

        assertThat(recordatorioService.enviarPendientes(LocalDateTime.of(2030, 3, 10, 10, 20))).isZero();
    }

    @Test
    void alReprogramarlo_vuelveAAvisar() throws Exception {
        int id = crearTrabajo("10:00");
        assertThat(recordatorioService.enviarPendientes(LocalDateTime.of(2030, 3, 10, 9, 5))).isEqualTo(1);

        mockMvc.perform(put("/api/ventas/trabajo-domicilio/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(objectMapper.writeValueAsString(trabajo("15:00"))))
            .andExpect(status().isOk());

        assertThat(recordatorioService.enviarPendientes(LocalDateTime.of(2030, 3, 10, 14, 10))).isEqualTo(1);
        assertThat(recordatorios()).isEqualTo(2);
    }
}
