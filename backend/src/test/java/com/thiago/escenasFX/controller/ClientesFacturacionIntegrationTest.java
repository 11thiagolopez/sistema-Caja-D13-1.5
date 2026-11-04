package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.service.AfipFacturacionService;

/**
 * Flujo de punta a punta de clientes, cuenta corriente, facturación y notas de crédito: cada regla
 * de plata se comprueba mirando el saldo o el arqueo que queda, no solo la respuesta del pedido.
 */
class ClientesFacturacionIntegrationTest extends AbstractIntegrationTest {

    private static final String CUIT = "20300238379";

    @Autowired
    private ProductoRepository productoRepo;

    private Empleado vendedor;
    private String tokenVendedor;
    private String tokenAdmin;
    private Producto producto;

    @BeforeEach
    void preparar() throws Exception {
        vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        tokenVendedor = login("vendedor1", "clave123");
        tokenAdmin = login("admin1", "clave123");

        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(10);
        p.setPrecioVenta(new BigDecimal("100"));
        producto = productoRepo.save(p);
    }

    private ResultActions enviar(String ruta, String token, String body) throws Exception {
        return mockMvc.perform(post(ruta)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .content(body));
    }

    private ResultActions pedir(String ruta, String token) throws Exception {
        return mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int idDe(ResultActions resultado, String campo) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get(campo).asInt();
    }

    private String cuerpoVenta(String medioPago, Integer idCliente, String extra) {
        return """
            {"idEmpleado": %d, "medioPago": "%s", %s %s
             "detalles": [{"idProducto": %d, "cantidad": 2, "precioUnitario": 100}]}
            """.formatted(vendedor.getIdEmpleado(), medioPago,
                idCliente != null ? "\"idCliente\": " + idCliente + "," : "", extra, producto.getIdProducto());
    }

    private int crearCliente(String nombre, String cuit) throws Exception {
        return idDe(enviar("/api/clientes", tokenAdmin, """
            {"nombre": "%s", "docTipo": 80, "docNro": "%s", "condicionIva": 1}
            """.formatted(nombre, cuit)).andExpect(status().isCreated()), "idCliente");
    }

    private int venderEfectivo() throws Exception {
        return idDe(enviar("/api/ventas", tokenVendedor, cuerpoVenta("EFECTIVO", null, ""))
            .andExpect(status().isOk()), "idVenta");
    }

    private void abrirCaja() throws Exception {
        enviar("/api/caja/abrir", tokenAdmin, "{\"idEmpleado\": " + vendedor.getIdEmpleado() + ", \"montoInicial\": 0}")
            .andExpect(status().isOk());
    }

    private void saldoEs(int idCliente, double saldo) throws Exception {
        pedir("/api/clientes/" + idCliente + "/saldo", tokenAdmin)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldo").value(saldo));
    }

    // --- Cuenta corriente ---

    @Test
    void venta_medioInvalido_devuelve400() throws Exception {
        enviar("/api/ventas", tokenVendedor, cuerpoVenta("CHEQUE", null, "")).andExpect(status().isBadRequest());
    }

    @Test
    void ventaEnCuentaCorriente_sinCliente_devuelve400() throws Exception {
        enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", null, "")).andExpect(status().isBadRequest());
    }

    @Test
    void ventaEnCuentaCorriente_conSaldoAplicado_devuelve400() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", idCliente, "\"montoSaldoAplicado\": 10,"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ventaEnCuentaCorriente_cargaAlSaldo_yNoEntraAlArqueoHastaQueSePaga() throws Exception {
        abrirCaja();
        int idCliente = crearCliente("Juan", CUIT);

        enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", idCliente, "")).andExpect(status().isOk());
        saldoEs(idCliente, -200);
        pedir("/api/caja/resumen-dia", tokenAdmin)
            .andExpect(jsonPath("$.efectivoFinal").value(0))
            .andExpect(jsonPath("$.ventasEnCuentaCorriente").value(200));

        enviar("/api/clientes/" + idCliente + "/pagos", tokenAdmin, "{\"monto\": 150, \"medioPago\": \"EFECTIVO\"}")
            .andExpect(status().isCreated());
        enviar("/api/clientes/" + idCliente + "/pagos", tokenAdmin, "{\"monto\": 50, \"medioPago\": \"TRANSFERENCIA\"}")
            .andExpect(status().isCreated());

        saldoEs(idCliente, 0);
        pedir("/api/caja/resumen-dia", tokenAdmin)
            .andExpect(jsonPath("$.efectivoFinal").value(150))
            .andExpect(jsonPath("$.totalDigital").value(50))
            .andExpect(jsonPath("$.pagosClienteEfectivo").value(150))
            .andExpect(jsonPath("$.totalDelDia").value(200));
    }

    @Test
    void pagoEnEfectivo_sinCajaAbierta_devuelve409() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        enviar("/api/clientes/" + idCliente + "/pagos", tokenAdmin, "{\"monto\": 10, \"medioPago\": \"EFECTIVO\"}")
            .andExpect(status().isConflict());
        saldoEs(idCliente, 0);
    }

    @Test
    void rechazarDescuento_ventaEnCuentaCorriente_revierteElCargo() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        int idVenta = idDe(enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", idCliente,
            "\"descuento\": 20, \"motivoDescuento\": \"amigo\",")).andExpect(status().isOk()), "idVenta");
        saldoEs(idCliente, -180);

        enviar("/api/autorizaciones/descuentos/" + idVenta + "/rechazar", tokenAdmin, "").andExpect(status().isOk());

        saldoEs(idCliente, 0);
    }

    @Test
    void darDeBaja_clienteConDeuda_devuelve409() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", idCliente, "")).andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/clientes/" + idCliente)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isConflict());
    }

    // --- Clientes ---

    @Test
    void crearCliente_contribuyenteSinCuit_devuelve400() throws Exception {
        enviar("/api/clientes", tokenAdmin, "{\"nombre\": \"Empresa\", \"docTipo\": 96, \"docNro\": \"30111222\", \"condicionIva\": 1}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void crearCliente_contribuyenteSinRazonSocial_devuelve400() throws Exception {
        enviar("/api/clientes", tokenAdmin, "{\"docTipo\": 80, \"docNro\": \"" + CUIT + "\", \"condicionIva\": 6}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void crearCliente_consumidorFinalSinDatos_devuelve400() throws Exception {
        enviar("/api/clientes", tokenAdmin, "{\"docTipo\": 99, \"condicionIva\": 5}").andExpect(status().isBadRequest());
    }

    @Test
    void crearCliente_consumidorFinalConDniSinNombre_seGuarda() throws Exception {
        enviar("/api/clientes", tokenAdmin, "{\"docTipo\": 96, \"docNro\": \"30111222\", \"condicionIva\": 5}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Consumidor Final"));
    }

    @Test
    void crearCliente_documentoRepetido_devuelve409() throws Exception {
        crearCliente("Juan", CUIT);
        enviar("/api/clientes", tokenVendedor, "{\"nombre\": \"Otro\", \"docTipo\": 80, \"docNro\": \"" + CUIT + "\", \"condicionIva\": 1}")
            .andExpect(status().isConflict());
    }

    @Test
    void editarCliente_conElDocumentoDeOtro_devuelve409() throws Exception {
        crearCliente("Juan", CUIT);
        int otro = crearCliente("Pedro", "27111111112");
        mockMvc.perform(put("/api/clientes/" + otro)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"nombre\": \"Pedro\", \"docTipo\": 80, \"docNro\": \"" + CUIT + "\", \"condicionIva\": 1}"))
            .andExpect(status().isConflict());
    }

    @Test
    void vendedor_buscaYVeElSaldoDelElegido_peroNoElResto() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);

        pedir("/api/clientes/buscar?q=jua", tokenVendedor)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombre").value("Juan"))
            .andExpect(jsonPath("$[0].saldo").doesNotExist());
        pedir("/api/clientes/" + idCliente + "/saldo", tokenVendedor).andExpect(status().isOk());

        pedir("/api/clientes", tokenVendedor).andExpect(status().isForbidden());
        pedir("/api/clientes/" + idCliente, tokenVendedor).andExpect(status().isForbidden());
        pedir("/api/clientes/" + idCliente + "/cuenta-corriente", tokenVendedor).andExpect(status().isForbidden());
        pedir("/api/clientes/" + idCliente + "/ventas", tokenVendedor).andExpect(status().isForbidden());
        enviar("/api/clientes/" + idCliente + "/pagos", tokenVendedor, "{\"monto\": 1, \"medioPago\": \"TARJETA\"}")
            .andExpect(status().isForbidden());
    }

    @Test
    void listarClientes_incluyeElSaldoDeCadaUno() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", idCliente, "")).andExpect(status().isOk());

        pedir("/api/clientes", tokenAdmin).andExpect(jsonPath("$[0].saldo").value(-200));
    }

    // --- Facturación ---

    @Test
    void facturarACliente_asignaElCliente_yLaVentaQuedaFacturada() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        int idVenta = venderEfectivo();

        pedir("/api/ventas/" + idVenta, tokenAdmin)
            .andExpect(jsonPath("$.estadoFactura").value("SIN_FACTURAR"))
            .andExpect(jsonPath("$.factura").doesNotExist());

        enviar("/api/ventas/" + idVenta + "/factura", tokenAdmin, "{\"idCliente\": " + idCliente + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.clienteNombre").value("Juan"))
            .andExpect(jsonPath("$.clienteDocNro").value(CUIT))
            .andExpect(jsonPath("$.condicionIvaReceptor").value(1));

        pedir("/api/ventas/" + idVenta, tokenAdmin)
            .andExpect(jsonPath("$.estadoFactura").value("FACTURADA"))
            .andExpect(jsonPath("$.idCliente").value(idCliente))
            .andExpect(jsonPath("$.factura.cae").exists());
    }

    @Test
    void facturar_arcaRechaza_quedaSinFacturarYSinCliente() throws Exception {
        when(afipFacturacionService.emitirFacturaC(any()))
            .thenReturn(new AfipFacturacionService.ResultadoCae(false, null, null, null, "10005 rechazado"));
        int idCliente = crearCliente("Juan", CUIT);
        int idVenta = venderEfectivo();

        enviar("/api/ventas/" + idVenta + "/factura", tokenAdmin, "{\"idCliente\": " + idCliente + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ERROR"));

        pedir("/api/ventas/" + idVenta, tokenAdmin)
            .andExpect(jsonPath("$.estadoFactura").value("SIN_FACTURAR"))
            .andExpect(jsonPath("$.idCliente").doesNotExist())
            .andExpect(jsonPath("$.factura.errorDetalle").value("10005 rechazado"));
    }

    @Test
    void historial_filtraPorEstadoDeFacturacion() throws Exception {
        int facturada = venderEfectivo();
        int sinFacturar = venderEfectivo();
        enviar("/api/ventas/" + facturada + "/factura", tokenAdmin, "{\"clienteDocTipo\": 99}").andExpect(status().isOk());

        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toString();
        String rango = "/api/ventas?desde=" + hoy + "&hasta=" + hoy;

        pedir(rango + "&estadoFactura=FACTURADA", tokenAdmin)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].idVenta").value(facturada));
        pedir(rango + "&estadoFactura=SIN_FACTURAR", tokenAdmin)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].idVenta").value(sinFacturar));
        pedir(rango + "&estadoFactura=OTRO", tokenAdmin).andExpect(status().isBadRequest());
    }

    @Test
    void facturar_ventaEnLaCuentaDeOtroCliente_devuelve409SinLlamarAArca() throws Exception {
        int juan = crearCliente("Juan", CUIT);
        int pedro = crearCliente("Pedro", "27111111112");
        int idVenta = idDe(enviar("/api/ventas", tokenVendedor, cuerpoVenta("CUENTA_CORRIENTE", juan, ""))
            .andExpect(status().isOk()), "idVenta");

        enviar("/api/ventas/" + idVenta + "/factura", tokenAdmin, "{\"idCliente\": " + pedro + "}")
            .andExpect(status().isConflict());

        verify(afipFacturacionService, never()).emitirFacturaC(any());
    }

    @Test
    void facturar_ventaConNotaDeCredito_devuelve409SinLlamarAArca() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        int idVenta = venderEfectivo();
        enviar("/api/notas-credito", tokenAdmin, """
            {"idVentaOriginal": %d, "idEmpleado": %d, "idCliente": %d, "motivo": "ERROR_TIPEO", "alcance": "TOTAL"}
            """.formatted(idVenta, vendedor.getIdEmpleado(), idCliente)).andExpect(status().isCreated());

        pedir("/api/ventas/" + idVenta, tokenAdmin).andExpect(jsonPath("$.tieneNotaCredito").value(true));
        enviar("/api/ventas/" + idVenta + "/factura", tokenAdmin, "{\"clienteDocTipo\": 99}")
            .andExpect(status().isConflict());

        verify(afipFacturacionService, never()).emitirFacturaC(any());
    }

    // --- Notas de crédito ---

    @Test
    void notaDeCredito_ventaConDescuento_acreditaLoCobradoYNoElPrecioDeLista() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        int idVenta = idDe(enviar("/api/ventas", tokenAdmin,
            cuerpoVenta("EFECTIVO", null, "\"descuento\": 20, \"motivoDescuento\": \"amigo\","))
            .andExpect(status().isOk()), "idVenta");

        enviar("/api/notas-credito", tokenAdmin, """
            {"idVentaOriginal": %d, "idEmpleado": %d, "idCliente": %d, "motivo": "ERROR_TIPEO", "alcance": "TOTAL"}
            """.formatted(idVenta, vendedor.getIdEmpleado(), idCliente))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.montoTotal").value(180));

        saldoEs(idCliente, 180);
    }

    @Test
    void notasParcialesSucesivas_conDescuentoQueNoDivideExacto_cierranEnLoCobrado() throws Exception {
        int idCliente = crearCliente("Juan", CUIT);
        String venta = """
            {"idEmpleado": %d, "medioPago": "EFECTIVO", "descuento": 0.01, "motivoDescuento": "redondeo",
             "detalles": [{"idProducto": %d, "cantidad": 3, "precioUnitario": 33.33}]}
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());
        var respuesta = objectMapper.readTree(enviar("/api/ventas", tokenAdmin, venta)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int idVenta = respuesta.get("idVenta").asInt();
        int idDetalle = respuesta.get("detalles").get(0).get("idDetalle").asInt();

        for (int i = 0; i < 3; i++) {
            enviar("/api/notas-credito", tokenAdmin, """
                {"idVentaOriginal": %d, "idEmpleado": %d, "idCliente": %d, "motivo": "ERROR_TIPEO", "alcance": "PARCIAL",
                 "detalles": [{"idDetalleVentaOriginal": %d, "cantidad": 1}]}
                """.formatted(idVenta, vendedor.getIdEmpleado(), idCliente, idDetalle))
                .andExpect(status().isCreated());
        }

        // Vendido 99,99 menos 0,01 de descuento.
        saldoEs(idCliente, 99.98);
    }

    // --- Permisos del vendedor sobre la factura ---

    @Test
    void vendedor_facturaYAbreSuVenta_peroNoLaDeOtro() throws Exception {
        int propia = venderEfectivo();

        enviar("/api/ventas/" + propia + "/factura", tokenVendedor, "{\"clienteDocTipo\": 99}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EMITIDA"));

        crearEmpleado("vendedor2", "clave123", "VENDEDOR", null);
        String tokenOtro = login("vendedor2", "clave123");

        enviar("/api/ventas/" + propia + "/factura", tokenOtro, "{\"clienteDocTipo\": 99}").andExpect(status().isForbidden());
        pedir("/api/ventas/" + propia + "/factura/pdf", tokenOtro).andExpect(status().isForbidden());
        pedir("/api/ventas/" + propia + "/factura", tokenVendedor).andExpect(status().isForbidden());
    }

    @Test
    void facturar_sinClienteNiDocumento_devuelve400() throws Exception {
        int idVenta = venderEfectivo();
        enviar("/api/ventas/" + idVenta + "/factura", tokenAdmin, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void facturarConsumidorFinal_mandaLosDatosCorrectosAArca() throws Exception {
        int idVenta = venderEfectivo();
        enviar("/api/ventas/" + idVenta + "/factura", tokenAdmin, "{\"clienteDocTipo\": 99}").andExpect(status().isOk());

        ArgumentCaptor<AfipFacturacionService.DatosFactura> datos =
            ArgumentCaptor.forClass(AfipFacturacionService.DatosFactura.class);
        verify(afipFacturacionService).emitirFacturaC(datos.capture());
        assertThat(datos.getValue().importe()).isEqualByComparingTo("200");
    }
}
