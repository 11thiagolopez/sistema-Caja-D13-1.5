package com.thiago.escenasFX.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Los días que se piden son de Argentina. Con -Duser.timezone=UTC reproduce a Railway: lo cargado a
 * las 22:30 ya está guardado con la fecha del día siguiente.
 */
class VentaListadoPorFechaIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private VentaRepository ventaRepo;

    private String tokenAdmin;

    @BeforeEach
    void loguearAdmin() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        tokenAdmin = login("admin1", "clave123");
    }

    private Integer guardarVenta(LocalDateTime fechaArgentina) {
        Venta v = new Venta();
        v.setFecha(fechaArgentina.atZone(ARGENTINA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        return ventaRepo.save(v).getIdVenta();
    }

    private ResultActions pedirDia(String dia) throws Exception {
        return mockMvc.perform(get("/api/ventas")
            .param("desde", dia).param("hasta", dia)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin));
    }

    @Test
    void listar_usaElDiaDeArgentina() throws Exception {
        Integer deNoche = guardarVenta(LocalDateTime.of(2026, 3, 3, 22, 30));
        Integer deMadrugada = guardarVenta(LocalDateTime.of(2026, 3, 4, 0, 10));

        pedirDia("2026-03-03")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idVenta", hasItem(deNoche)))
            .andExpect(jsonPath("$[*].idVenta", not(hasItem(deMadrugada))));

        pedirDia("2026-03-04")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idVenta", hasItem(deMadrugada)))
            .andExpect(jsonPath("$[*].idVenta", not(hasItem(deNoche))));
    }
}
