package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

/**
 * Flujo completo: venta real (controller -> VentaService) -> nota de crédito parcial sobre esa
 * venta (controller -> NotaCreditoService) -> stock devuelto + saldo acreditado al cliente, todo
 * contra H2 real con JWT real (igual espíritu que VentaControllerIntegrationTest).
 */
class NotaCreditoControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductoRepository productoRepo;

    private Producto crearProducto(int stockInicial) {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockInicial);
        p.setPrecioVenta(new BigDecimal("1000"));
        return productoRepo.save(p);
    }

    @Test
    void crearNotaDeCredito_devolucionParcial_devuelveStockYAcreditaSaldo() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");
        String tokenAdmin = login("admin1", "clave123");

        // 1. Registrar la venta de mostrador (3 unidades).
        String ventaBody = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 3, "precioUnitario": 1000}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        String ventaRespuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(ventaBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode ventaJson = objectMapper.readTree(ventaRespuesta);
        int idVenta = ventaJson.get("idVenta").asInt();
        int idDetalle = ventaJson.get("detalles").get(0).get("idDetalle").asInt();

        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(7);

        // 2. Crear un cliente.
        String clienteRespuesta = mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"nombre": "Cliente de prueba", "docTipo": 99}"""))
            .andReturn().getResponse().getContentAsString();
        int idCliente = objectMapper.readTree(clienteRespuesta).get("idCliente").asInt();

        // 3. Nota de crédito parcial: devuelve solo 2 de las 3 unidades, motivo devolución.
        String ncBody = """
            {
                "idVentaOriginal": %d,
                "idEmpleado": %d,
                "idCliente": %d,
                "motivo": "DEVOLUCION_MERCADERIA",
                "alcance": "PARCIAL",
                "detalles": [{"idDetalleVentaOriginal": %d, "cantidad": 2}]
            }
            """.formatted(idVenta, admin.getIdEmpleado(), idCliente, idDetalle);

        mockMvc.perform(post("/api/notas-credito")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(ncBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.montoTotal").value(2000))
            .andExpect(jsonPath("$.esFiscal").value(false))
            .andExpect(jsonPath("$.estado").value("EMITIDA"));

        // Stock: 7 (tras la venta) + 2 (devueltas) = 9.
        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(9);

        // 4. La cuenta corriente del cliente refleja el crédito.
        mockMvc.perform(get("/api/clientes/" + idCliente + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoActual").value(2000))
            .andExpect(jsonPath("$.movimientos[0].tipo").value("CREDITO"));
    }

    @Test
    void crearNotaDeCredito_pidiendoMasQueElRemanente_devuelve409() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");
        String tokenAdmin = login("admin1", "clave123");

        String ventaBody = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 1000}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());
        String ventaRespuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(ventaBody))
            .andReturn().getResponse().getContentAsString();
        JsonNode ventaJson = objectMapper.readTree(ventaRespuesta);
        int idVenta = ventaJson.get("idVenta").asInt();
        int idDetalle = ventaJson.get("detalles").get(0).get("idDetalle").asInt();

        String clienteRespuesta = mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"nombre": "Cliente de prueba", "docTipo": 99}"""))
            .andReturn().getResponse().getContentAsString();
        int idCliente = objectMapper.readTree(clienteRespuesta).get("idCliente").asInt();

        // Pide acreditar 5 unidades de una línea que solo tiene 1.
        String ncBody = """
            {
                "idVentaOriginal": %d,
                "idEmpleado": %d,
                "idCliente": %d,
                "motivo": "DEVOLUCION_MERCADERIA",
                "alcance": "PARCIAL",
                "detalles": [{"idDetalleVentaOriginal": %d, "cantidad": 5}]
            }
            """.formatted(idVenta, admin.getIdEmpleado(), idCliente, idDetalle);

        mockMvc.perform(post("/api/notas-credito")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(ncBody))
            .andExpect(status().isConflict());
    }

    @Test
    void notaDeCreditoSobreVentaFacturada_quedaPendienteFiscalYSeEmiteAnteArca() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");
        String tokenAdmin = login("admin1", "clave123");

        String ventaBody = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 1000}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());
        String ventaRespuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(ventaBody))
            .andReturn().getResponse().getContentAsString();
        JsonNode ventaJson = objectMapper.readTree(ventaRespuesta);
        int idVenta = ventaJson.get("idVenta").asInt();

        // Factura fiscal sobre la venta (Consumidor Final) — AfipFacturacionService está
        // mockeado en AbstractIntegrationTest para devolver un CAE aprobado siempre.
        mockMvc.perform(post("/api/ventas/" + idVenta + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"clienteDocTipo\": 99}"))
            .andExpect(status().isOk());

        String clienteRespuesta = mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"nombre": "Cliente de prueba", "docTipo": 99}"""))
            .andReturn().getResponse().getContentAsString();
        int idCliente = objectMapper.readTree(clienteRespuesta).get("idCliente").asInt();

        String ncBody = """
            {
                "idVentaOriginal": %d,
                "idEmpleado": %d,
                "idCliente": %d,
                "motivo": "DEVOLUCION_MERCADERIA",
                "alcance": "TOTAL"
            }
            """.formatted(idVenta, admin.getIdEmpleado(), idCliente);

        String ncRespuesta = mockMvc.perform(post("/api/notas-credito")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(ncBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.esFiscal").value(true))
            .andExpect(jsonPath("$.estado").value("PENDIENTE_FISCAL"))
            .andReturn().getResponse().getContentAsString();
        int idNotaCredito = objectMapper.readTree(ncRespuesta).get("idNotaCredito").asInt();

        mockMvc.perform(post("/api/notas-credito/" + idNotaCredito + "/emitir-fiscal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.tipoComprobante").value(13))
            .andExpect(jsonPath("$.cae").isNotEmpty());

        mockMvc.perform(get("/api/notas-credito/" + idNotaCredito + "/factura-pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void crearNotaDeCredito_comoVendedor_devuelve403() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/notas-credito")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idVentaOriginal": 1, "idEmpleado": %d, "motivo": "ERROR_TIPEO", "alcance": "TOTAL"}
                    """.formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isForbidden());
    }
}
