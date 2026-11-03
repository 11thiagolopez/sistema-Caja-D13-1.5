package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

/**
 * Ejercita el flujo completo de venta (controller -> VentaService -> repositorios -> H2 real,
 * más autenticación JWT real). Las peticiones con
 * descuento están en AutorizacionControllerIntegrationTest.
 */
class VentaControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductoRepository productoRepo;

    private Producto crearProducto(int stockInicial) {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockInicial);
        p.setPrecioVenta(new BigDecimal("100"));
        return productoRepo.save(p);
    }

    @Test
    void registrarVenta_sinDescuento_quedaConfirmadaYDescuentaStock() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Producto producto = crearProducto(10);
        String token = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 3, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
            .andExpect(jsonPath("$.totalVenta").value(300));

        Producto actualizado = productoRepo.findById(producto.getIdProducto()).orElseThrow();
        assertThat(actualizado.getStockActual()).isEqualTo(7);
    }

    @Test
    void registrarVenta_incluyeProveedorDelProductoEnElDetalle() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Producto producto = crearProducto(10);
        producto.setProveedor("Proveedor Test");
        producto = productoRepo.save(producto);
        String token = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.detalles[0].proveedorProducto").value("Proveedor Test"));
    }

    @Test
    void registrarVenta_stockInsuficiente_devuelve409() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Producto producto = crearProducto(1);
        String token = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 5, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void registrarVenta_comoVendedor_estaPermitido() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Producto producto = crearProducto(10);
        String token = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    void listarVentasPorRango_comoAdmin_devuelveLasRegistradas() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(body))
            .andExpect(status().isOk());

        String tokenAdmin = login("admin1", "clave123");
        java.time.LocalDate hoy = java.time.LocalDate.now();

        mockMvc.perform(get("/api/ventas")
                .param("desde", hoy.toString())
                .param("hasta", hoy.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void registrarVenta_pagadaConSaldoDeCuentaCorriente_fuerzaMedioPagoYDebita() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto productoDevuelto = crearProducto(10);
        Producto productoNuevo = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");
        String tokenAdmin = login("admin1", "clave123");

        // 1. Venta original + nota de crédito total -> $100 de saldo a favor para el cliente.
        String ventaOriginalBody = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), productoDevuelto.getIdProducto());
        String ventaOriginalResp = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(ventaOriginalBody))
            .andReturn().getResponse().getContentAsString();
        int idVentaOriginal = objectMapper.readTree(ventaOriginalResp).get("idVenta").asInt();

        String clienteResp = mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"nombre": "Cliente de prueba", "docTipo": 99}"""))
            .andReturn().getResponse().getContentAsString();
        int idCliente = objectMapper.readTree(clienteResp).get("idCliente").asInt();

        mockMvc.perform(post("/api/notas-credito")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"idVentaOriginal": %d, "idEmpleado": %d, "idCliente": %d,
                     "motivo": "ANULACION_TOTAL", "alcance": "TOTAL"}
                    """.formatted(idVentaOriginal, admin.getIdEmpleado(), idCliente)))
            .andExpect(status().isCreated());

        // 2. Venta nueva, pagada 100% con ese saldo — el frontend manda "EFECTIVO" pero el
        // backend tiene que ignorarlo y forzar SALDO_CUENTA_CORRIENTE porque cubre el total.
        String ventaNuevaBody = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "idCliente": %d,
                "montoSaldoAplicado": 100,
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), idCliente, productoNuevo.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(ventaNuevaBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.medioPago").value("SALDO_CUENTA_CORRIENTE"))
            .andExpect(jsonPath("$.montoSaldoAplicado").value(100));

        // 3. El saldo del cliente quedó en $0 (se acreditaron $100, se debitaron $100).
        mockMvc.perform(get("/api/clientes/" + idCliente + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoActual").value(0))
            .andExpect(jsonPath("$.movimientos.length()").value(2));
    }

    @Test
    void registrarVenta_saldoSuperaElDisponible_devuelve409() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");
        String tokenAdmin = login("admin1", "clave123");

        String clienteResp = mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("""
                    {"nombre": "Cliente sin saldo", "docTipo": 99}"""))
            .andReturn().getResponse().getContentAsString();
        int idCliente = objectMapper.readTree(clienteResp).get("idCliente").asInt();

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "idCliente": %d,
                "montoSaldoAplicado": 50,
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado(), idCliente, producto.getIdProducto());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void registrarVenta_productoInexistente_devuelve400() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": 999999, "cantidad": 1, "precioUnitario": 100}]
            }
            """.formatted(vendedor.getIdEmpleado());

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body))
            .andExpect(status().isBadRequest());
    }
}
