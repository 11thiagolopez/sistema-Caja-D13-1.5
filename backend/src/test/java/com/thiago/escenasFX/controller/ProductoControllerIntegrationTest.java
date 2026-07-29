package com.thiago.escenasFX.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * ABM de productos (alta con código interno autogenerado, baja lógica, carga de stock por
 * código) contra H2 real y autenticación JWT real.
 */
class ProductoControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PRODUCTO_BODY = """
        {"rubro": "01", "familia": "05", "marca": "02", "proveedor": "Proveedor SA",
         "descripcion": "Destornillador Stanley", "precioVenta": 500, "precioCompra": 300,
         "stockActual": 20, "codigoFabrica": "7791234567890"}""";

    private String tokenAdmin() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        return login("admin1", "clave123");
    }

    private String tokenVendedor() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        return login("vendedor1", "clave123");
    }

    @Test
    void crear_comoAdmin_generaCodigoInternoConCorrelativo() throws Exception {
        String token = tokenAdmin();

        mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(PRODUCTO_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigoInterno").value("0105020001"))
            .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(PRODUCTO_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigoInterno").value("0105020002"));
    }

    @Test
    void crear_comoVendedor_devuelve403() throws Exception {
        String token = tokenVendedor();

        mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(PRODUCTO_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_comoAdmin_seDaDeBajaYaNoApareceEnListado() throws Exception {
        String token = tokenAdmin();

        String respuesta = mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(PRODUCTO_BODY))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Integer idProducto = objectMapper.readTree(respuesta).get("idProducto").asInt();

        mockMvc.perform(delete("/api/productos/" + idProducto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.idProducto == " + idProducto + ")]").isEmpty());
    }

    @Test
    void eliminar_comoVendedor_devuelve403() throws Exception {
        String tokenAdmin = tokenAdmin();
        String respuesta = mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(PRODUCTO_BODY))
            .andReturn().getResponse().getContentAsString();
        Integer idProducto = objectMapper.readTree(respuesta).get("idProducto").asInt();

        String tokenVendedor = tokenVendedor();

        mockMvc.perform(delete("/api/productos/" + idProducto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
    }

    @Test
    void cargarStock_comoAdmin_sumaCantidadAlStockExistente() throws Exception {
        String token = tokenAdmin();

        mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(PRODUCTO_BODY))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/productos/cargar-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"codigo": "7791234567890", "cantidad": 5}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockActual").value(25));
    }

    @Test
    void cargarStock_comoVendedor_devuelve403() throws Exception {
        String token = tokenVendedor();

        mockMvc.perform(post("/api/productos/cargar-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"codigo": "7791234567890", "cantidad": 5}"""))
            .andExpect(status().isForbidden());
    }

    @Test
    void buscarPorCodigo_comoVendedor_encuentraPorCodigoInterno() throws Exception {
        String tokenAdmin = tokenAdmin();
        mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(PRODUCTO_BODY))
            .andExpect(status().isCreated());

        String tokenVendedor = tokenVendedor();

        mockMvc.perform(get("/api/productos/buscar-por-codigo")
                .param("codigo", "0105020001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.descripcion").value("Destornillador Stanley"));
    }

    @Test
    void buscarPorCodigo_noExiste_devuelve400() throws Exception {
        String token = tokenAdmin();

        mockMvc.perform(get("/api/productos/buscar-por-codigo")
                .param("codigo", "no-existe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isBadRequest());
    }
}
