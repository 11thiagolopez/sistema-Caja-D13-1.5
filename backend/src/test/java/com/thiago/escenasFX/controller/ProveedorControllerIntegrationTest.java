package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Cuenta corriente de proveedores (fase 4): compra con pago parcial -> deuda pendiente -> pago
 * posterior -> saldo en cero, y devolución de mercadería -> stock devuelto + crédito a favor de
 * D13. Todo contra H2 real con JWT real, mismo espíritu que los demás tests de integración.
 */
class ProveedorControllerIntegrationTest extends AbstractIntegrationTest {

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
    void compraConPagoParcial_pagoPosterior_dejaSaldoPendienteEnCero() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(0);
        String token = login("admin1", "clave123");

        String compraBody = """
            {
                "idEmpleado": %d,
                "fecha": "%s",
                "proveedorNombre": "Proveedor de prueba",
                "medioPago": "EFECTIVO",
                "montoPagado": 30,
                "items": [{"idProducto": %d, "cantidad": 1, "precioCompraUnitario": 100}]
            }
            """.formatted(admin.getIdEmpleado(), java.time.LocalDate.now(), producto.getIdProducto());

        String compraResp = mockMvc.perform(post("/api/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(compraBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.montoPagado").value(30))
            .andExpect(jsonPath("$.saldoPendiente").value(70))
            .andReturn().getResponse().getContentAsString();
        int idCompra = objectMapper.readTree(compraResp).get("idCompra").asInt();
        int idProveedor = objectMapper.readTree(compraResp).get("idProveedor").asInt();

        mockMvc.perform(get("/api/proveedores/" + idProveedor + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoPendiente").value(70))
            .andExpect(jsonPath("$.movimientos.length()").value(2));

        mockMvc.perform(post("/api/compras/" + idCompra + "/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "monto": 70, "medioPago": "TRANSFERENCIA"}
                    """.formatted(admin.getIdEmpleado())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoPendiente").value(0));

        mockMvc.perform(get("/api/proveedores/" + idProveedor + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoPendiente").value(0))
            .andExpect(jsonPath("$.movimientos.length()").value(3));
    }

    @Test
    void registrarPago_superaElSaldoPendiente_devuelve409() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(0);
        String token = login("admin1", "clave123");

        String compraBody = """
            {
                "idEmpleado": %d,
                "fecha": "%s",
                "proveedorNombre": "Proveedor de prueba",
                "medioPago": "EFECTIVO",
                "montoPagado": 0,
                "items": [{"idProducto": %d, "cantidad": 1, "precioCompraUnitario": 100}]
            }
            """.formatted(admin.getIdEmpleado(), java.time.LocalDate.now(), producto.getIdProducto());

        String compraResp = mockMvc.perform(post("/api/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(compraBody))
            .andReturn().getResponse().getContentAsString();
        int idCompra = objectMapper.readTree(compraResp).get("idCompra").asInt();

        mockMvc.perform(post("/api/compras/" + idCompra + "/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "monto": 150, "medioPago": "EFECTIVO"}
                    """.formatted(admin.getIdEmpleado())))
            .andExpect(status().isConflict());
    }

    @Test
    void registrarDevolucion_devuelveStockYAcreditaLaCuentaCorriente() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(0);
        String token = login("admin1", "clave123");

        String compraBody = """
            {
                "idEmpleado": %d,
                "fecha": "%s",
                "proveedorNombre": "Proveedor de prueba",
                "medioPago": "EFECTIVO",
                "items": [{"idProducto": %d, "cantidad": 10, "precioCompraUnitario": 100}]
            }
            """.formatted(admin.getIdEmpleado(), java.time.LocalDate.now(), producto.getIdProducto());

        String compraResp = mockMvc.perform(post("/api/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(compraBody))
            .andReturn().getResponse().getContentAsString();
        int idProveedor = objectMapper.readTree(compraResp).get("idProveedor").asInt();

        mockMvc.perform(post("/api/proveedores/devoluciones")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {
                        "idProveedor": %d,
                        "idEmpleado": %d,
                        "motivo": "Mercadería defectuosa",
                        "detalles": [{"idProducto": %d, "cantidad": 3, "precioUnitario": 100}]
                    }
                    """.formatted(idProveedor, admin.getIdEmpleado(), producto.getIdProducto())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.montoTotal").value(300));

        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(7);

        // La compra fue 1000 (10*100) pagada del todo -> saldo 0; la devolución de 300 deja el
        // proveedor "a favor" de D13 (saldo negativo).
        mockMvc.perform(get("/api/proveedores/" + idProveedor + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoPendiente").value(-300));
    }

    @Test
    void registrarDevolucion_comoVendedor_devuelve403() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/proveedores/devoluciones")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idProveedor": 1, "idEmpleado": %d, "detalles": [{"idProducto": 1, "cantidad": 1, "precioUnitario": 10}]}
                    """.formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isForbidden());
    }
}
