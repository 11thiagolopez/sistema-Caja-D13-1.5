package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.service.AfipFacturacionService;
import com.thiago.escenasFX.service.AfipFacturacionService.ResultadoCae;

/**
 * Ejercita FacturaFiscalController de punta a punta (controller -> FacturaFiscalService ->
 * repositorios -> H2 real, con AfipFacturacionService mockeado como en AbstractIntegrationTest)
 * más las reglas de SecurityConfig específicas de facturación fiscal.
 */
class FacturaFiscalControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductoRepository productoRepo;

    private Producto crearProducto() {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(10);
        p.setPrecioVenta(new BigDecimal("100"));
        return productoRepo.save(p);
    }

    /** Registra y confirma (sin descuento) una venta real vía la API, devuelve su idVenta. */
    private Integer crearVentaConfirmada(String tokenVendedor, Empleado vendedor, Producto producto) throws Exception {
        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 2, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        String respuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(respuesta).get("idVenta").asInt();
    }

    @Test
    void facturar_ventaConfirmadaComoAdmin_emiteFacturaConCae() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);

        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.cae").value("12345678901234"))
            .andExpect(jsonPath("$.idVenta").value(idVenta));
    }

    @Test
    void facturar_comoVendedor_devuelve403() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void facturar_ventaInexistente_devuelve400() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/999999/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void facturar_cuitSinDocumento_devuelve400() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 80, \"clienteNombre\": \"Juan Perez\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void facturar_cuitSinNombre_devuelve400() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 80, \"clienteDocNro\": \"20300238379\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void facturar_conCuitYNombre_emiteFacturaConNombreDelCliente() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 80, \"clienteDocNro\": \"20300238379\", "
                    + "\"clienteNombre\": \"Juan Perez\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.clienteNombre").value("Juan Perez"))
            .andExpect(jsonPath("$.clienteDocNro").value("20300238379"));
    }

    @Test
    void facturar_yaFacturada_devuelve409() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isConflict());
    }

    @Test
    void obtener_sinFactura_devuelve204() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(get("/api/ventas/" + idVenta + "/factura")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isNoContent());
    }

    @Test
    void obtener_conFactura_devuelveDatos() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/ventas/" + idVenta + "/factura")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.cae").value("12345678901234"));
    }

    @Test
    void descargarPdf_sinFactura_devuelve400() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(get("/api/ventas/999999/factura/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isBadRequest());
    }

    @Test
    void descargarPdf_conFacturaEmitida_devuelvePdfValido() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk());

        byte[] pdf = mockMvc.perform(get("/api/ventas/" + idVenta + "/factura/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType(MediaType.APPLICATION_PDF))
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).isNotEmpty();
        // Encabezado estándar de un archivo PDF válido ("%PDF-").
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void enviarEmail_emailInvalido_devuelve400() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura/enviar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"email\": \"no-es-un-email\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void enviarEmail_sinFactura_devuelve400() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/999999/factura/enviar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"email\": \"cliente@test.com\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void enviarEmail_conFactura_enviaAdjuntoYDevuelve204() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto();
        String tokenVendedor = login("vendedor1", "clave123");
        Integer idVenta = crearVentaConfirmada(tokenVendedor, vendedor, producto);
        String tokenAdmin = login("admin1", "clave123");

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura/enviar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"email\": \"cliente@test.com\"}"))
            .andExpect(status().isNoContent());

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarConAdjuntoPdf(emailCaptor.capture(), anyString(), anyString(), anyString(), any());
        assertThat(emailCaptor.getValue()).isEqualTo("cliente@test.com");
    }

    /*
     * Regresion de un bug real encontrado escribiendo estos tests: el matcher de SecurityConfig
     * para facturacion fiscal solo cubria el path exacto "/api/ventas/{idVenta}/factura" (un
     * segmento), y no alcanzaba a "/factura/pdf" ni "/factura/enviar-email" (un segmento mas):
     * esos dos sub-endpoints quedaban sin matcher propio y caian en el
     * anyRequest().authenticated() generico del final, asi que cualquier rol autenticado
     * (VENDEDOR incluido) podia descargar o mandar por mail una factura fiscal ajena. Se agrego
     * un matcher adicional con doble comodin para cubrir los sub-endpoints.
     */
    @Test
    void descargarPdf_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/ventas/999999/factura/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
    }

    /** Mismo bug de matcher que el test de arriba, mismo comentario. */
    @Test
    void enviarEmail_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/ventas/999999/factura/enviar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content("{\"email\": \"cliente@test.com\"}"))
            .andExpect(status().isForbidden());
    }
}
