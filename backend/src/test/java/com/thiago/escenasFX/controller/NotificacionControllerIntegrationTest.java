package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Notificacion;
import com.thiago.escenasFX.repository.NotificacionRepository;

/** La campanita: cada empleado ve y marca como leídas únicamente sus propias notificaciones. */
class NotificacionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificacionRepository notificacionRepo;

    private Empleado fabian;
    private Empleado otroTecnico;
    private String tokenFabian;
    private String tokenOtroTecnico;

    @BeforeEach
    void prepararDatos() throws Exception {
        fabian = crearEmpleado("fabian", "clave123", "TECNICO", null);
        otroTecnico = crearEmpleado("otrotecnico", "clave123", "TECNICO", null);
        tokenFabian = login("fabian", "clave123");
        tokenOtroTecnico = login("otrotecnico", "clave123");
    }

    private Notificacion crearPara(Empleado empleado, boolean leida) {
        Notificacion n = new Notificacion();
        n.setEmpleado(empleado);
        n.setTipo("TRABAJO_NUEVO");
        n.setTexto("Nuevo trabajo: Belgrano 233");
        n.setLeida(leida);
        return notificacionRepo.save(n);
    }

    @Test
    void listar_soloTraeLasPropias() throws Exception {
        crearPara(fabian, false);
        crearPara(otroTecnico, false);

        mockMvc.perform(get("/api/notificaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].texto").value("Nuevo trabajo: Belgrano 233"));
    }

    @Test
    void contarNoLeidas_cuentaSoloLasPropiasSinLeer() throws Exception {
        crearPara(fabian, false);
        crearPara(fabian, true);
        crearPara(otroTecnico, false);

        mockMvc.perform(get("/api/notificaciones/no-leidas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidad").value(1));
    }

    @Test
    void marcarLeida_propia_laMarca() throws Exception {
        Notificacion n = crearPara(fabian, false);

        mockMvc.perform(post("/api/notificaciones/" + n.getIdNotificacion() + "/leida")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notificaciones/no-leidas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    void marcarLeida_deOtroEmpleado_rechaza() throws Exception {
        Notificacion n = crearPara(fabian, false);

        mockMvc.perform(post("/api/notificaciones/" + n.getIdNotificacion() + "/leida")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtroTecnico))
            .andExpect(status().isBadRequest());
    }
}
