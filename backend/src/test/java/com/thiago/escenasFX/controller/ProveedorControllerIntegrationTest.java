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
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;

/**
 * Cuenta corriente de proveedores (fase 4): compra con pago parcial -> deuda pendiente -> pago
 * posterior -> saldo en cero, y devolución de mercadería -> stock devuelto + crédito a favor de
 * D13. Todo contra H2 real con JWT real, mismo espíritu que los demás tests de integración.
 */
class ProveedorControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private SesionCajaRepository sesionRepo;

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
                "origen": "EXTERNO",
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
                    {"idEmpleado": %d, "monto": 70, "medioPago": "TRANSFERENCIA", "origen": "EXTERNO"}
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
                    {"idEmpleado": %d, "monto": 150, "medioPago": "EFECTIVO", "origen": "EXTERNO"}
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
                "origen": "EXTERNO",
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
    void compraEnCuentaCorriente_seSaldaConPagoParcialYLuegoTotal_descontandoDevoluciones() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(0);
        String token = login("admin1", "clave123");

        String compraResp = mockMvc.perform(post("/api/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {
                        "idEmpleado": %d,
                        "fecha": "%s",
                        "proveedorNombre": "Proveedor de prueba",
                        "medioPago": "CUENTA_CORRIENTE",
                        "items": [{"idProducto": %d, "cantidad": 10, "precioCompraUnitario": 100}]
                    }
                    """.formatted(admin.getIdEmpleado(), java.time.LocalDate.now(), producto.getIdProducto())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.medioPago").value("CUENTA_CORRIENTE"))
            .andExpect(jsonPath("$.saldoPendiente").value(1000))
            .andReturn().getResponse().getContentAsString();
        int idProveedor = objectMapper.readTree(compraResp).get("idProveedor").asInt();

        mockMvc.perform(post("/api/proveedores/devoluciones")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {
                        "idProveedor": %d,
                        "idEmpleado": %d,
                        "detalles": [{"idProducto": %d, "cantidad": 3, "precioUnitario": 100}]
                    }
                    """.formatted(idProveedor, admin.getIdEmpleado(), producto.getIdProducto())))
            .andExpect(status().isCreated());

        String pago = """
            {"idEmpleado": %d, "monto": %%d, "medioPago": "TRANSFERENCIA", "origen": "EXTERNO"}
            """.formatted(admin.getIdEmpleado());

        // La deuda real es 700: pagar los 1000 de la compra sería pagarle de más al proveedor.
        mockMvc.perform(post("/api/proveedores/" + idProveedor + "/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(pago.formatted(1000)))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/proveedores/" + idProveedor + "/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(pago.formatted(200)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoPendiente").value(500))
            .andExpect(jsonPath("$.comprasPendientes[0].saldoPendiente").value(500));

        mockMvc.perform(post("/api/proveedores/" + idProveedor + "/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(pago.formatted(500)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoPendiente").value(0))
            .andExpect(jsonPath("$.comprasPendientes.length()").value(0));
    }

    @Test
    void arqueo_muestraTodosLosMovimientosPeroSoloRestaElEfectivoDeCaja() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(0);
        String token = login("admin1", "clave123");

        SesionCaja sesion = new SesionCaja();
        sesion.setFecha(java.time.LocalDate.now());
        sesion.setMontoInicial(new BigDecimal("1000"));
        sesion.setEstado("ABIERTA");
        sesionRepo.save(sesion);

        String compra = """
            {
                "idEmpleado": %d,
                "fecha": "%s",
                "proveedorNombre": "Proveedor de prueba",
                "medioPago": "%%s",
                "origen": "%%s",
                "items": [{"idProducto": %d, "cantidad": 1, "precioCompraUnitario": %%d}]
            }
            """.formatted(admin.getIdEmpleado(), java.time.LocalDate.now(), producto.getIdProducto());

        for (String body : java.util.List.of(
                compra.formatted("EFECTIVO", "CAJA", 100),
                compra.formatted("EFECTIVO", "EXTERNO", 200),
                compra.formatted("TRANSFERENCIA", "CAJA", 50),
                compra.formatted("TRANSFERENCIA", "EXTERNO", 70),
                compra.formatted("CUENTA_CORRIENTE", "CAJA", 900))) {
            mockMvc.perform(post("/api/compras")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .content(body))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/caja/resumen-dia")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.movimientosProveedor.length()").value(5))
            .andExpect(jsonPath("$.pagosProveedorEfectivoCaja").value(100))
            .andExpect(jsonPath("$.pagosProveedorTransferenciaCaja").value(50))
            .andExpect(jsonPath("$.efectivoFinal").value(900))
            .andExpect(jsonPath("$.totalDigital").value(-50))
            .andExpect(jsonPath("$.movimientosProveedor[?(@.restaDelSaldoEsperado == true)].monto").value(100));
    }

    @Test
    void compraPagadaDeCaja_sinCajaAbierta_devuelve409YNoRegistraNada() throws Exception {
        Empleado admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(0);
        String token = login("admin1", "clave123");

        mockMvc.perform(post("/api/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {
                        "idEmpleado": %d,
                        "fecha": "%s",
                        "proveedorNombre": "Proveedor de prueba",
                        "medioPago": "EFECTIVO",
                        "origen": "CAJA",
                        "items": [{"idProducto": %d, "cantidad": 1, "precioCompraUnitario": 100}]
                    }
                    """.formatted(admin.getIdEmpleado(), java.time.LocalDate.now(), producto.getIdProducto())))
            .andExpect(status().isConflict());
    }

    @Test
    void registrarPagoAProveedor_comoVendedor_devuelve403() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String token = login("vendedor1", "clave123");

        mockMvc.perform(post("/api/proveedores/1/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "monto": 10, "medioPago": "EFECTIVO", "origen": "CAJA"}
                    """.formatted(vendedor.getIdEmpleado())))
            .andExpect(status().isForbidden());
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
