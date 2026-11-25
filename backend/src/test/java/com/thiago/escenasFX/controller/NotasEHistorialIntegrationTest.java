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

/** Notas del jefe (avisan al técnico) e historial de trabajos del mismo cliente o dirección. */
class NotasEHistorialIntegrationTest extends AbstractIntegrationTest {

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

    private int crear(String telefono, String direccion, String fecha) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", fabian.getIdEmpleado());
        cuerpo.put("clienteNombre", "Carlos Ferreyra");
        cuerpo.put("clienteTelefono", telefono);
        cuerpo.put("direccionTrabajo", direccion);
        cuerpo.put("descripcionTrabajo", "Cerradura de doble paleta");
        cuerpo.put("fechaProgramada", fecha);
        cuerpo.put("estadoTrabajo", "AGENDADO");
        cuerpo.put("cerrar", false);
        String r = mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(r).get("idVenta").asInt();
    }

    @Test
    void notaDelJefe_quedaEnLaListaYLeAvisaAlTecnico() throws Exception {
        int id = crear("1155551234", "Soldados 750", "2030-03-10");

        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/notas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"texto\": \"Llevá la escalera grande\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/mis-trabajos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(jsonPath("$.notas[0].texto").value("Llevá la escalera grande"));
        String avisos = mockMvc.perform(get("/api/notificaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(avisos).contains("Nota del jefe en #" + id + ": Llevá la escalera grande");
    }

    @Test
    void notaDelJefe_esSoloDelJefe() throws Exception {
        int id = crear("1155551234", "Soldados 750", "2030-03-10");

        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/notas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"texto\": \"hola\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void historial_encuentraPorTelefonoConOtroFormatoOPorDireccion_yExcluyeElActual() throws Exception {
        int viejoMismoTel = crear("11 5555-1234", "Otra calle 100", "2030-01-05");
        int viejoMismaDir = crear("1199998888", "SOLDADOS 750", "2030-02-05");
        crear("1177776666", "Nada que ver 1", "2030-02-06");
        int actual = crear("1155551234", "Soldados 750", "2030-03-10");

        String r = mockMvc.perform(get("/api/ventas/trabajo-domicilio/historial-cliente")
                .param("telefono", "+54 9 11 5555-1234")
                .param("direccion", "Soldados 750")
                .param("excluir", String.valueOf(actual))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode lista = objectMapper.readTree(r);

        assertThat(lista.size()).isEqualTo(2);
        // Del más reciente al más viejo.
        assertThat(lista.get(0).get("idVenta").asInt()).isEqualTo(viejoMismaDir);
        assertThat(lista.get(1).get("idVenta").asInt()).isEqualTo(viejoMismoTel);
    }
}
