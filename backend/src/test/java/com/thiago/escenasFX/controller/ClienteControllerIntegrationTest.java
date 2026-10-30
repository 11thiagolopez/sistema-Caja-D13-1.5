package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Empleado;

class ClienteControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void crearYListarCliente_comoAdmin_funciona() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        String body = """
            {"nombre": "Juan Pérez", "docTipo": 96, "docNro": "30111222"}
            """;

        mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Juan Pérez"))
            .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(get("/api/clientes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void crearCliente_comoVendedor_devuelve403() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login(vendedor.getUsuario(), "clave123");

        String body = """
            {"nombre": "Juan Pérez", "docTipo": 99}
            """;

        mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void cuentaCorriente_clienteSinMovimientos_devuelveSaldoCero() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String token = login("admin1", "clave123");

        String crearBody = """
            {"nombre": "Juan Pérez", "docTipo": 99}
            """;
        String respuesta = mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(crearBody))
            .andReturn().getResponse().getContentAsString();
        Integer idCliente = objectMapper.readTree(respuesta).get("idCliente").asInt();

        mockMvc.perform(get("/api/clientes/" + idCliente + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoActual").value(0))
            .andExpect(jsonPath("$.movimientos.length()").value(0));
    }
}
