package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

/**
 * Peticiones de retiro y descuento de punta a punta: el VENDEDOR pide, el ADMIN aprueba o rechaza
 * desde el sistema. Sin códigos ni mails de por medio.
 */
class AutorizacionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private ClienteRepository clienteRepo;

    @Autowired
    private MovimientoCuentaCorrienteClienteRepository movimientoRepo;

    private Empleado admin;
    private Empleado vendedor;
    private String tokenAdmin;
    private String tokenVendedor;

    @BeforeEach
    void crearEmpleados() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        tokenAdmin = login("admin1", "clave123");
        tokenVendedor = login("vendedor1", "clave123");
    }

    private Producto crearProducto(int stock) {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stock);
        p.setPrecioVenta(new BigDecimal("100"));
        return productoRepo.save(p);
    }

    private int solicitarRetiro(String token, Empleado empleado) throws Exception {
        String respuesta = mockMvc.perform(post("/api/caja/retiro/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "monto": 300, "motivo": "Gastos", "medioPago": "EFECTIVO"}"""
                    .formatted(empleado.getIdEmpleado())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("idSolicitud").asInt();
    }

    private String ventaConDescuento(Empleado empleado, Producto producto) {
        return """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 2, "precioUnitario": 100}],
                "descuento": 50,
                "motivoDescuento": "Cliente frecuente"
            }
            """.formatted(empleado.getIdEmpleado(), producto.getIdProducto());
    }

    private int registrarVentaPendiente(Producto producto) throws Exception {
        String respuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(ventaConDescuento(vendedor, producto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE_AUTORIZACION"))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("idVenta").asInt();
    }

    private org.springframework.test.web.servlet.ResultActions resolver(String token, String ruta) throws Exception {
        return mockMvc.perform(post("/api/autorizaciones/" + ruta)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void retiroDeVendedor_apareceEnPendientesYAlAprobarRecienSaleDeLaCaja() throws Exception {
        int id = solicitarRetiro(tokenVendedor, vendedor);

        mockMvc.perform(get("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tipo").value("RETIRO"))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].monto").value(300))
            .andExpect(jsonPath("$[0].solicitante").value("Test vendedor1"));
        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.retirosEfectivo").value(0));

        resolver(tokenAdmin, "retiros/" + id + "/aprobar")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("APROBADA"));

        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.retirosEfectivo").value(300));
        mockMvc.perform(get("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void retiroRechazado_noSaleDeLaCaja() throws Exception {
        int id = solicitarRetiro(tokenVendedor, vendedor);

        resolver(tokenAdmin, "retiros/" + id + "/rechazar")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.retirosEfectivo").value(0));
    }

    /** Una petición ya resuelta no se puede volver a resolver: la plata saldría dos veces. */
    @Test
    void retiroYaResuelto_noSePuedeResolverDeNuevo() throws Exception {
        int id = solicitarRetiro(tokenVendedor, vendedor);
        resolver(tokenAdmin, "retiros/" + id + "/aprobar").andExpect(status().isOk());

        resolver(tokenAdmin, "retiros/" + id + "/aprobar").andExpect(status().isConflict());
        resolver(tokenAdmin, "retiros/" + id + "/rechazar").andExpect(status().isConflict());

        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.retirosEfectivo").value(300));
    }

    @Test
    void retiroDeAdmin_quedaConfirmadoDeUnaYNoEsUnaPeticion() throws Exception {
        solicitarRetiro(tokenAdmin, admin);

        mockMvc.perform(get("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/caja/resumen-dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.retirosEfectivo").value(300));
    }

    /** El idEmpleado viene en el body: mandar el de un ADMIN no saltea la petición ni cambia quién figura. */
    @Test
    void retiroDeVendedorMandandoElIdDeUnAdmin_quedaPendienteYANombreDelVendedor() throws Exception {
        int id = solicitarRetiro(tokenVendedor, admin);

        mockMvc.perform(get("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].solicitante").value("Test vendedor1"));
    }

    @Test
    void descuentoAprobado_confirmaLaVentaYElStockQuedaDescontado() throws Exception {
        Producto producto = crearProducto(10);
        int id = registrarVentaPendiente(producto);

        mockMvc.perform(get("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$[0].tipo").value("DESCUENTO"))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].monto").value(50))
            .andExpect(jsonPath("$[0].totalVenta").value(150));

        resolver(tokenAdmin, "descuentos/" + id + "/aprobar")
            .andExpect(jsonPath("$.estado").value("APROBADA"));

        mockMvc.perform(get("/api/ventas/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(8);
    }

    /**
     * El vendedor imprime lo que cobró, pero el comprobante no puede servirle para recorrer ventas
     * ajenas ni para entregar uno con un descuento que todavía nadie aprobó.
     */
    @Test
    void comprobante_vendedorSoloVeElDeSusVentasConfirmadas() throws Exception {
        crearEmpleado("vendedor2", "clave123", "VENDEDOR", null);
        String tokenOtro = login("vendedor2", "clave123");
        int id = registrarVentaPendiente(crearProducto(10));

        mockMvc.perform(get("/api/ventas/" + id + "/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());

        resolver(tokenAdmin, "descuentos/" + id + "/aprobar").andExpect(status().isOk());

        mockMvc.perform(get("/api/ventas/" + id + "/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/" + id + "/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOtro))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/" + id + "/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk());
        // El historial sigue siendo exclusivo de ADMIN.
        mockMvc.perform(get("/api/ventas/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
    }

    @Test
    void descuentoRechazado_anulaLaVentaYDevuelveElStock() throws Exception {
        Producto producto = crearProducto(10);
        int id = registrarVentaPendiente(producto);
        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(8);

        resolver(tokenAdmin, "descuentos/" + id + "/rechazar")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        mockMvc.perform(get("/api/ventas/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.estado").value("ANULADA"));
        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(10);

        // Una venta anulada no se puede facturar ni volver a resolver (no devuelve el stock dos veces).
        resolver(tokenAdmin, "descuentos/" + id + "/rechazar").andExpect(status().isConflict());
        assertThat(productoRepo.findById(producto.getIdProducto()).orElseThrow().getStockActual()).isEqualTo(10);
        mockMvc.perform(post("/api/ventas/" + id + "/factura")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteDocTipo\": 99}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isConflict());
    }

    @Test
    void descuentoRechazado_devuelveElSaldoDeCuentaCorrienteUsado() throws Exception {
        Producto producto = crearProducto(10);
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente");
        cliente.setDocNro("20111111112");
        cliente = clienteRepo.save(cliente);
        MovimientoCuentaCorrienteCliente credito = new MovimientoCuentaCorrienteCliente();
        credito.setCliente(cliente);
        credito.setTipo("CREDITO");
        credito.setMonto(new BigDecimal("100"));
        movimientoRepo.save(credito);

        String respuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content("""
                    {
                        "idEmpleado": %d,
                        "medioPago": "EFECTIVO",
                        "detalles": [{"idProducto": %d, "cantidad": 2, "precioUnitario": 100}],
                        "descuento": 50,
                        "motivoDescuento": "Cliente frecuente",
                        "idCliente": %d,
                        "montoSaldoAplicado": 60
                    }
                    """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto(), cliente.getIdCliente())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(respuesta).get("idVenta").asInt();

        mockMvc.perform(get("/api/clientes/" + cliente.getIdCliente() + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.saldoActual").value(40));

        resolver(tokenAdmin, "descuentos/" + id + "/rechazar").andExpect(status().isOk());

        mockMvc.perform(get("/api/clientes/" + cliente.getIdCliente() + "/cuenta-corriente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.saldoActual").value(100));
    }

    @Test
    void descuentoDeAdmin_quedaConfirmadoDeUna() throws Exception {
        Producto producto = crearProducto(10);

        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(ventaConDescuento(admin, producto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    void vendedor_noPuedeVerPeticionesNiResolverlas() throws Exception {
        int id = solicitarRetiro(tokenVendedor, vendedor);

        mockMvc.perform(get("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
        resolver(tokenVendedor, "retiros/" + id + "/aprobar").andExpect(status().isForbidden());
        resolver(tokenVendedor, "retiros/" + id + "/rechazar").andExpect(status().isForbidden());
        resolver(tokenVendedor, "descuentos/1/aprobar").andExpect(status().isForbidden());
        resolver(tokenVendedor, "descuentos/1/rechazar").andExpect(status().isForbidden());
    }

    @Test
    void estado_devuelveComoQuedaronLasPropiasEIgnoraLasAjenas() throws Exception {
        Empleado otro = crearEmpleado("vendedor2", "clave123", "VENDEDOR", null);
        String tokenOtro = login("vendedor2", "clave123");
        int retiroPropio = solicitarRetiro(tokenVendedor, vendedor);
        int retiroAjeno = solicitarRetiro(tokenOtro, otro);
        int descuentoPropio = registrarVentaPendiente(crearProducto(10));
        resolver(tokenAdmin, "retiros/" + retiroPropio + "/aprobar").andExpect(status().isOk());

        mockMvc.perform(get("/api/autorizaciones/estado")
                .param("retiros", retiroPropio + "," + retiroAjeno)
                .param("descuentos", String.valueOf(descuentoPropio))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].tipo").value("RETIRO"))
            .andExpect(jsonPath("$[0].id").value(retiroPropio))
            .andExpect(jsonPath("$[0].estado").value("APROBADA"))
            .andExpect(jsonPath("$[1].tipo").value("DESCUENTO"))
            .andExpect(jsonPath("$[1].estado").value("PENDIENTE"));
    }
}
