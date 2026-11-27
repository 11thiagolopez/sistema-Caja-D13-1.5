package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.service.MemoriaAlmacenamientoArchivos;

/**
 * Videos y audios adjuntos a un trabajo a domicilio: mismas reglas que las fotos (los suben jefe y
 * técnico, cada uno borra lo suyo y el jefe cualquiera), hasta 5 videos por trabajo. El técnico puede
 * marcarlos antes/después o subirlos como adjunto. El archivo va al almacenamiento (en los tests,
 * memoria), no a la base.
 */
class ArchivosTrabajoIntegrationTest extends AbstractIntegrationTest {

    // Cabeceras mínimas: MP4 ("....ftypisom"), MOV de iPhone ("....ftypqt  "), nota de voz de WhatsApp
    // (OGG/OPUS) y audio M4A ("....ftypM4A ").
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
    private static final byte[] MOV = {0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' ', 0, 0, 0, 0};
    private static final byte[] OPUS = {'O', 'g', 'g', 'S', 0, 2, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] M4A = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ', 0, 0, 0, 0};

    @Autowired
    private MemoriaAlmacenamientoArchivos almacenamiento;

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

    private MockMultipartFile archivo(byte[] bytes) {
        return new MockMultipartFile("archivo", "archivo.bin", "application/octet-stream", bytes);
    }

    private int noLeidas(String token) throws Exception {
        String r = mockMvc.perform(get("/api/notificaciones/no-leidas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("cantidad").asInt();
    }

    private int subirComoJefe(int idTrabajo, byte[] bytes) throws Exception {
        String r = mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + idTrabajo + "/archivos")
                .file(archivo(bytes))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var lista = objectMapper.readTree(r);
        return lista.get(lista.size() - 1).get("idArchivo").asInt();
    }

    @Test
    void jefeAdjuntaUnVideo_elTecnicoLoVe_recibeElAviso_yPideElLink() throws Exception {
        int id = crearTrabajo();
        int avisosAntes = noLeidas(tokenFabian);

        int idArchivo = subirComoJefe(id, MP4);

        assertThat(noLeidas(tokenFabian)).isEqualTo(avisosAntes + 1);
        mockMvc.perform(get("/api/mis-trabajos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(jsonPath("$.archivos.length()").value(1))
            .andExpect(jsonPath("$.archivos[0].tamanioBytes").value(MP4.length))
            .andExpect(jsonPath("$.archivos[0].tipoContenido").value("video/mp4"))
            .andExpect(jsonPath("$.archivos[0].momento").doesNotExist());
        mockMvc.perform(get("/api/mis-trabajos/" + id + "/archivos/" + idArchivo + "/url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("data:video/mp4;")));
        // Otro técnico no ve el archivo de un trabajo que no es suyo.
        mockMvc.perform(get("/api/mis-trabajos/" + id + "/archivos/" + idArchivo + "/url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico))
            .andExpect(status().isForbidden());
    }

    @Test
    void audiosDeWhatsAppYM4A_seAceptanComoAudio() throws Exception {
        int id = crearTrabajo();

        subirComoJefe(id, OPUS);
        subirComoJefe(id, M4A);

        mockMvc.perform(get("/api/ventas/trabajo-domicilio/" + id + "/archivos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$[0].tipoContenido").value("audio/ogg"))
            .andExpect(jsonPath("$[1].tipoContenido").value("audio/mp4"));
    }

    @Test
    void tecnicoSubeConAntesODespues_oComoAdjunto_yElJefeSeEntera() throws Exception {
        int id = crearTrabajo();
        int avisosAntes = noLeidas(tokenAdmin);

        mockMvc.perform(multipart("/api/mis-trabajos/" + id + "/archivos")
                .file(archivo(MOV))
                .param("momento", "ANTES")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].momento").value("ANTES"))
            .andExpect(jsonPath("$[0].idEmpleado").value(fabian.getIdEmpleado()));
        // "Adjuntar": sin momento.
        mockMvc.perform(multipart("/api/mis-trabajos/" + id + "/archivos")
                .file(archivo(OPUS))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[1].momento").doesNotExist());
        // Un momento inventado no se adivina.
        mockMvc.perform(multipart("/api/mis-trabajos/" + id + "/archivos")
                .file(archivo(OPUS))
                .param("momento", "DURANTE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isBadRequest());

        assertThat(noLeidas(tokenAdmin)).isEqualTo(avisosAntes + 2);
    }

    @Test
    void tecnicoNoPuedeBorrarUnArchivoDelJefe_peroElJefeSi_ySeVaDelAlmacenamiento() throws Exception {
        int id = crearTrabajo();
        int archivosAntes = almacenamiento.cantidad();
        int idArchivo = subirComoJefe(id, MP4);
        assertThat(almacenamiento.cantidad()).isEqualTo(archivosAntes + 1);

        mockMvc.perform(delete("/api/mis-trabajos/" + id + "/archivos/" + idArchivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ventas/trabajo-domicilio/" + id + "/archivos/" + idArchivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isNoContent());

        assertThat(almacenamiento.cantidad()).isEqualTo(archivosAntes);
    }

    @Test
    void tecnicoBorraElSuyo() throws Exception {
        int id = crearTrabajo();
        String r = mockMvc.perform(multipart("/api/mis-trabajos/" + id + "/archivos")
                .file(archivo(MP4))
                .param("momento", "DESPUES")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int idArchivo = objectMapper.readTree(r).get(0).get("idArchivo").asInt();

        mockMvc.perform(delete("/api/mis-trabajos/" + id + "/archivos/" + idArchivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isNoContent());
    }

    @Test
    void masDeCincoVideos_rechazaCon400_peroLosAudiosTienenSuPropioTope() throws Exception {
        int id = crearTrabajo();
        for (int i = 0; i < 5; i++) {
            subirComoJefe(id, MP4);
        }
        int archivosAntes = almacenamiento.cantidad();

        mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/archivos")
                .file(archivo(MP4))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isBadRequest());
        assertThat(almacenamiento.cantidad()).isEqualTo(archivosAntes);

        subirComoJefe(id, OPUS);
    }

    @Test
    void unArchivoQueNoEsVideoNiAudio_rechazaCon400_yNoGuardaNada() throws Exception {
        int id = crearTrabajo();
        int archivosAntes = almacenamiento.cantidad();

        mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/archivos")
                .file(archivo("MZ no soy un video".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isBadRequest());
        // Una foto tampoco: las fotos van por /fotos (se comprimen y quedan en la base).
        mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/archivos")
                .file(archivo(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16}))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isBadRequest());
        assertThat(almacenamiento.cantidad()).isEqualTo(archivosAntes);
    }
}
