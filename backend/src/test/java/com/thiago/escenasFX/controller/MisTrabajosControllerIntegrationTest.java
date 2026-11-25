package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.thiago.escenasFX.model.Empleado;

/**
 * "Mis trabajos" del técnico por HTTP: cada técnico ve y actualiza solo lo suyo, nunca lo de otro
 * (ni aunque adivine el id), y no puede tocar un trabajo ya cerrado o cancelado.
 */
class MisTrabajosControllerIntegrationTest extends AbstractIntegrationTest {

    private Empleado admin;
    private Empleado fabian;
    private Empleado otroTecnico;
    private String tokenAdmin;
    private String tokenFabian;
    private String tokenOtroTecnico;

    @BeforeEach
    void prepararDatos() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        fabian = crearEmpleado("fabian", "clave123", "TECNICO", null);
        otroTecnico = crearEmpleado("otrotecnico", "clave123", "TECNICO", null);
        tokenAdmin = login("admin1", "clave123");
        tokenFabian = login("fabian", "clave123");
        tokenOtroTecnico = login("otrotecnico", "clave123");
    }

    private int crearTrabajoAsignadoAFabian() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", fabian.getIdEmpleado());
        cuerpo.put("clienteNombre", "Juan Pérez");
        cuerpo.put("direccionTrabajo", "Belgrano 233");
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
    void listar_comoFabian_soloVeElTrabajoAsignadoAEl() throws Exception {
        int idPropio = crearTrabajoAsignadoAFabian();

        String respuesta = mockMvc.perform(get("/api/mis-trabajos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode lista = objectMapper.readTree(respuesta);

        assertThat(lista.size()).isEqualTo(1);
        assertThat(lista.get(0).get("trabajo").get("idVenta").asInt()).isEqualTo(idPropio);
    }

    @Test
    void listar_comoOtroTecnico_noVeElTrabajoDeFabian() throws Exception {
        crearTrabajoAsignadoAFabian();

        mockMvc.perform(get("/api/mis-trabajos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void obtener_deOtroTecnico_devuelve403() throws Exception {
        int id = crearTrabajoAsignadoAFabian();

        mockMvc.perform(get("/api/mis-trabajos/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico))
            .andExpect(status().isForbidden());
    }

    @Test
    void cambiarEstado_propio_actualizaYQuedaEnElDetalle() throws Exception {
        int id = crearTrabajoAsignadoAFabian();

        mockMvc.perform(post("/api/mis-trabajos/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"estado\": \"EN_CAMINO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trabajo.estadoTrabajo").value("EN_CAMINO"));

        mockMvc.perform(get("/api/mis-trabajos/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(jsonPath("$.trabajo.estadoTrabajo").value("EN_CAMINO"));
    }

    @Test
    void cambiarEstado_deOtroTecnico_devuelve403() throws Exception {
        int id = crearTrabajoAsignadoAFabian();

        mockMvc.perform(post("/api/mis-trabajos/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico)
                .content("{\"estado\": \"EN_CAMINO\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void cambiarEstado_aUnoQueElTecnicoNoPuedeFijar_rechaza() throws Exception {
        int id = crearTrabajoAsignadoAFabian();

        mockMvc.perform(post("/api/mis-trabajos/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"estado\": \"CERRADO_Y_COBRADO\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarEstado_trabajoYaCancelado_rechaza() throws Exception {
        int id = crearTrabajoAsignadoAFabian();
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/anular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/mis-trabajos/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"estado\": \"EN_CAMINO\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void agregarNota_propio_quedaEnElDetalle() throws Exception {
        int id = crearTrabajoAsignadoAFabian();

        mockMvc.perform(post("/api/mis-trabajos/" + id + "/notas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"texto\": \"Falta un repuesto\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.texto").value("Falta un repuesto"))
            .andExpect(jsonPath("$.nombreEmpleado").value("Test fabian"));

        mockMvc.perform(get("/api/mis-trabajos/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(jsonPath("$.notas.length()").value(1))
            .andExpect(jsonPath("$.notas[0].texto").value("Falta un repuesto"));
    }

    @Test
    void notasDelTrabajo_comoAdmin_vePuestasPorElTecnico() throws Exception {
        int id = crearTrabajoAsignadoAFabian();
        mockMvc.perform(post("/api/mis-trabajos/" + id + "/notas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"texto\": \"Falta un repuesto\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/ventas/trabajo-domicilio/" + id + "/notas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].texto").value("Falta un repuesto"));
    }

    @Test
    void agregarNota_deOtroTecnico_devuelve403() throws Exception {
        int id = crearTrabajoAsignadoAFabian();

        mockMvc.perform(post("/api/mis-trabajos/" + id + "/notas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico)
                .content("{\"texto\": \"intento ajeno\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void comoAdmin_noPuedeUsarMisTrabajos() throws Exception {
        mockMvc.perform(get("/api/mis-trabajos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isForbidden());
    }
}
