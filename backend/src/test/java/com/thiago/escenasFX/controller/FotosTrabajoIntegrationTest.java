package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.thiago.escenasFX.model.Empleado;

/**
 * Fotos compartidas de un trabajo a domicilio: el jefe sube (ej. las que le mandó el cliente) y el
 * técnico las ve; el técnico sube y el jefe se entera. Y lo que el técnico NO recibe: el teléfono
 * del cliente.
 */
class FotosTrabajoIntegrationTest extends AbstractIntegrationTest {

    // Cabecera mínima de un JPEG: alcanza para la detección por bytes de FotoTrabajoService.
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    private Empleado admin;
    private Empleado fabian;
    private String tokenAdmin;
    private String tokenFabian;
    private String tokenOtroTecnico;

    @BeforeEach
    void prepararDatos() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        fabian = crearEmpleado("fabian", "clave123", "TECNICO", null);
        crearEmpleado("otrotecnico", "clave123", "TECNICO", null);
        tokenAdmin = login("admin1", "clave123");
        tokenFabian = login("fabian", "clave123");
        tokenOtroTecnico = login("otrotecnico", "clave123");
    }

    private int crearTrabajo() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", fabian.getIdEmpleado());
        cuerpo.put("clienteNombre", "Carlos Ferreyra");
        cuerpo.put("clienteTelefono", "1155556666");
        cuerpo.put("direccionTrabajo", "Soldados 750");
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

    private MockMultipartFile foto(byte[] bytes) {
        return new MockMultipartFile("fotos", "foto.jpg", "image/jpeg", bytes);
    }

    private int noLeidas(String token) throws Exception {
        String r = mockMvc.perform(get("/api/notificaciones/no-leidas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("cantidad").asInt();
    }

    @Test
    void detalleDelTecnico_noTraeElTelefonoDelCliente() throws Exception {
        int id = crearTrabajo();

        String r = mockMvc.perform(get("/api/mis-trabajos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trabajo.clienteNombre").value("Carlos Ferreyra"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(r).doesNotContain("1155556666").doesNotContain("clienteTelefono").doesNotContain("clienteEmail");
    }

    @Test
    void adminSubeFoto_elTecnicoLaVeYRecibeElAviso() throws Exception {
        int id = crearTrabajo();
        int avisosAntes = noLeidas(tokenFabian);

        String r = mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/fotos")
                .file(foto(JPEG)).file(foto(JPEG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int idFoto = objectMapper.readTree(r).get(0).get("idFoto").asInt();

        // Un solo aviso por las dos fotos.
        assertThat(noLeidas(tokenFabian)).isEqualTo(avisosAntes + 1);

        mockMvc.perform(get("/api/mis-trabajos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(jsonPath("$.fotos.length()").value(2));
        mockMvc.perform(get("/api/mis-trabajos/" + id + "/fotos/" + idFoto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(content().bytes(JPEG));
        mockMvc.perform(get("/api/mis-trabajos/" + id + "/fotos/" + idFoto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico))
            .andExpect(status().isForbidden());
    }

    @Test
    void tecnicoSubeFoto_elAdminLaVeYRecibeElAviso() throws Exception {
        int id = crearTrabajo();
        int avisosAntes = noLeidas(tokenAdmin);

        mockMvc.perform(multipart("/api/mis-trabajos/" + id + "/fotos")
                .file(foto(JPEG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isCreated());

        assertThat(noLeidas(tokenAdmin)).isEqualTo(avisosAntes + 1);
        String r = mockMvc.perform(get("/api/ventas/trabajo-domicilio/" + id + "/fotos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode fotos = objectMapper.readTree(r);
        assertThat(fotos.size()).isEqualTo(1);
        assertThat(fotos.get(0).get("idEmpleado").asInt()).isEqualTo(fabian.getIdEmpleado());
    }

    @Test
    void tecnicoNoPuedeBorrarUnaFotoDelJefe_peroElJefeSi() throws Exception {
        int id = crearTrabajo();
        String r = mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/fotos")
                .file(foto(JPEG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int idFoto = objectMapper.readTree(r).get(0).get("idFoto").asInt();

        mockMvc.perform(delete("/api/mis-trabajos/" + id + "/fotos/" + idFoto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ventas/trabajo-domicilio/" + id + "/fotos/" + idFoto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isNoContent());
    }

    @Test
    void unArchivoQueNoEsImagen_rechazaCon400() throws Exception {
        int id = crearTrabajo();

        mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/fotos")
                .file(new MockMultipartFile("fotos", "virus.jpg", "image/jpeg", "MZ no soy una foto".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isBadRequest());
    }
}
