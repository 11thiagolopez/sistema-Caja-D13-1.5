package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;
import com.thiago.escenasFX.service.ZonaNegocio;

/**
 * Conciliación de trabajos a domicilio de punta a punta por HTTP: lo cobrado tiene que coincidir con
 * el stock y con los reportes, sin entrar al arqueo de caja. Los valores esperados están hechos a
 * mano. Con -Duser.timezone=UTC reproduce a Railway. Corre contra H2: el gráfico de ventas por día de
 * mostrador, que usa AT TIME ZONE de Postgres, no se ejercita acá.
 */
class TrabajoDomicilioConciliacionIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private VentaRepository ventaRepo;

    @Autowired
    private SesionCajaRepository sesionRepo;

    @Autowired
    private FacturaFiscalRepository facturaRepo;

    @Autowired
    private NotaCreditoRepository notaCreditoRepo;

    @Autowired
    private ClienteRepository clienteRepo;

    private Empleado admin;
    private Empleado tecnico;
    private Producto cerradura;
    private String token;
    private LocalDate hoy;

    @BeforeEach
    void prepararDatos() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        admin.setComision(new BigDecimal("5"));
        tecnico = crearEmpleado("tecnico1", "clave123", "TECNICO", null);
        tecnico.setComision(new BigDecimal("10"));
        token = login("admin1", "clave123");
        hoy = ZonaNegocio.hoy();

        cerradura = new Producto();
        cerradura.setDescripcion("Cerradura doble paleta");
        cerradura.setMarca("KALOP");
        cerradura.setStockActual(10);
        cerradura.setPrecioVenta(new BigDecimal("100"));
        cerradura.setPrecioCompra(new BigDecimal("60"));
        cerradura = productoRepo.save(cerradura);
    }

    private Map<String, Object> articulo(int cantidad) {
        Map<String, Object> linea = new HashMap<>();
        linea.put("idProducto", cerradura.getIdProducto());
        linea.put("tipo", "ARTICULO");
        linea.put("cantidad", cantidad);
        linea.put("precioUnitario", 100);
        return linea;
    }

    private Map<String, Object> manoDeObra(int precio) {
        Map<String, Object> linea = new HashMap<>();
        linea.put("descripcion", "Instalación");
        linea.put("tipo", "SERVICIO");
        linea.put("cantidad", 1);
        linea.put("precioUnitario", precio);
        return linea;
    }

    private Map<String, Object> trabajo(String medioPago, boolean cerrar, List<Map<String, Object>> lineas) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("clienteNombre", "Juan Pérez");
        cuerpo.put("direccionTrabajo", "Belgrano 233");
        cuerpo.put("estadoTrabajo", "AGENDADO");
        cuerpo.put("medioPago", medioPago);
        cuerpo.put("cerrar", cerrar);
        cuerpo.put("detalles", lineas);
        return cuerpo;
    }

    private JsonNode crear(Map<String, Object> cuerpo) throws Exception {
        String respuesta = mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta);
    }

    private int crearId(Map<String, Object> cuerpo) throws Exception {
        return crear(cuerpo).get("idVenta").asInt();
    }

    private int crearDevolviendoStatus(Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andReturn().getResponse().getStatus();
    }

    private int actualizarDevolviendoStatus(int id, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(put("/api/ventas/trabajo-domicilio/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andReturn().getResponse().getStatus();
    }

    private void actualizar(int id, Map<String, Object> cuerpo) throws Exception {
        assertThat(actualizarDevolviendoStatus(id, cuerpo)).isEqualTo(200);
    }

    private int anularDevolviendoStatus(int id) throws Exception {
        return mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/anular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn().getResponse().getStatus();
    }

    private void abrirCaja(int montoInicial) throws Exception {
        mockMvc.perform(post("/api/caja/abrir")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"idEmpleado": %d, "montoInicial": %d}""".formatted(admin.getIdEmpleado(), montoInicial)))
            .andExpect(status().isOk());
    }

    private void venderEnMostrador(String medioPago, int cantidad) throws Exception {
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idEmpleado", admin.getIdEmpleado(),
                    "medioPago", medioPago,
                    "detalles", List.of(articulo(cantidad))))))
            .andExpect(status().isOk());
    }

    private JsonNode pedir(String ruta, LocalDate desde, LocalDate hasta) throws Exception {
        return pedir(ruta, desde, hasta, "limit", "10");
    }

    private JsonNode pedir(String ruta, LocalDate desde, LocalDate hasta, String parametro, String valor) throws Exception {
        String respuesta = mockMvc.perform(get(ruta)
                .param("desde", desde.toString())
                .param("hasta", hasta.toString())
                .param(parametro, valor)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta);
    }

    private JsonNode reporte(LocalDate desde, LocalDate hasta) throws Exception {
        return pedir("/api/reportes/trabajos-domicilio", desde, hasta);
    }

    private JsonNode arqueo(LocalDate dia) throws Exception {
        return pedir("/api/caja/resumen", dia, dia).get("total");
    }

    private JsonNode balance(LocalDate dia) throws Exception {
        return pedir("/api/reportes/balance", dia, dia);
    }

    private JsonNode buscar(JsonNode lista, String campo, String valor) {
        for (JsonNode fila : lista) {
            if (valor.equals(fila.get(campo).asText())) {
                return fila;
            }
        }
        throw new AssertionError("No está " + campo + "=" + valor + " en " + lista);
    }

    private void assertMonto(JsonNode nodo, String campo, String esperado) {
        assertThat(nodo.get(campo).decimalValue()).as(campo).isEqualByComparingTo(esperado);
    }

    private int stock() {
        return productoRepo.findById(cerradura.getIdProducto()).orElseThrow().getStockActual();
    }

    /** Un día completo: una venta de mostrador, trabajos con cada medio de pago, un borrador y uno anulado. */
    private void cargarDiaCompleto() throws Exception {
        abrirCaja(1000);
        venderEnMostrador("EFECTIVO", 1);

        Map<String, Object> conTecnico = trabajo("EFECTIVO", true, List.of(articulo(2), manoDeObra(3000)));
        conTecnico.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        crear(conTecnico);

        crear(trabajo("TRANSFERENCIA", true, List.of(manoDeObra(1500))));

        Map<String, Object> conTarjeta = trabajo("TARJETA", true, List.of(articulo(1), manoDeObra(700)));
        conTarjeta.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        crear(conTarjeta);

        crear(trabajo("EFECTIVO", false, List.of(articulo(1), manoDeObra(9000))));

        int aAnular = crearId(trabajo("EFECTIVO", false, List.of(articulo(1), manoDeObra(8000))));
        assertThat(anularDevolviendoStatus(aAnular)).isEqualTo(200);
    }

    @Test
    void stock_borradorReserva_editarAjustaLaDiferencia_yCobrarNoVuelveADescontar() throws Exception {
        int id = crearId(trabajo("EFECTIVO", false, List.of(articulo(3))));
        assertThat(stock()).isEqualTo(7);

        actualizar(id, trabajo("EFECTIVO", false, List.of(articulo(5))));
        assertThat(stock()).isEqualTo(5);

        actualizar(id, trabajo("EFECTIVO", false, List.of(articulo(2))));
        assertThat(stock()).isEqualTo(8);

        actualizar(id, trabajo("EFECTIVO", true, List.of(articulo(2))));
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void stock_quitarElArticuloDelBorrador_loDevuelveCompleto() throws Exception {
        int id = crearId(trabajo("EFECTIVO", false, List.of(articulo(4), manoDeObra(500))));
        assertThat(stock()).isEqualTo(6);

        actualizar(id, trabajo("EFECTIVO", false, List.of(manoDeObra(500))));

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void stock_manoDeObraNoLoTocaNiFiguraEnElRanking() throws Exception {
        crear(trabajo("EFECTIVO", true, List.of(manoDeObra(2000))));

        assertThat(stock()).isEqualTo(10);
        assertThat(pedir("/api/reportes/productos-ganadores", hoy, hoy)).isEmpty();
    }

    @Test
    void stock_anularDevuelveLoReservado() throws Exception {
        int id = crearId(trabajo("EFECTIVO", false, List.of(articulo(4))));
        assertThat(stock()).isEqualTo(6);

        assertThat(anularDevolviendoStatus(id)).isEqualTo(200);

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void stock_insuficiente_rechazaSinTocarlo() throws Exception {
        assertThat(crearDevolviendoStatus(trabajo("EFECTIVO", false, List.of(articulo(11))))).isEqualTo(409);

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void conciliacion_stockDelDiaCompleto() throws Exception {
        cargarDiaCompleto();

        // 10 - 1 (mostrador) - 2 (efectivo) - 1 (tarjeta) - 1 (borrador); el anulado volvió.
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void conciliacion_elArqueoSoloCuentaMostrador_yLosTrabajosSeSumanAparte() throws Exception {
        cargarDiaCompleto();

        JsonNode total = arqueo(hoy);

        assertMonto(total, "montoInicial", "1000");
        assertMonto(total, "ventasEfectivo", "100");
        assertMonto(total, "ventasTransferencia", "0");
        assertMonto(total, "ventasTarjeta", "0");
        assertMonto(total, "efectivoFinal", "1100");
        assertMonto(total, "totalDigital", "0");
        assertMonto(total, "cajaTotalDelDia", "1100");
        assertMonto(total, "totalTrabajosDomicilio", "5500");
        assertMonto(total, "totalDelDia", "5600");
    }

    @Test
    void trabajo_noSeVinculaALaCaja() throws Exception {
        abrirCaja(1000);

        int id = crearId(trabajo("EFECTIVO", true, List.of(manoDeObra(1000))));

        assertThat(ventaRepo.findById(id).orElseThrow().getSesion()).isNull();
        JsonNode turno = pedir("/api/caja/resumen", hoy, hoy).get("sesiones").get(0).get("resumen");
        assertMonto(turno, "efectivoFinal", "1000");
        assertMonto(turno, "totalTrabajosDomicilio", "0");
    }

    @Test
    void trabajoViejo_vinculadoAUnaSesion_noEntraAlTurno() throws Exception {
        abrirCaja(1000);
        int id = crearId(trabajo("EFECTIVO", true, List.of(manoDeObra(1000))));
        SesionCaja sesion = sesionRepo.findByEstado("ABIERTA").orElseThrow();
        ventaRepo.findById(id).orElseThrow().setSesion(sesion);

        JsonNode turno = pedir("/api/caja/resumen", hoy, hoy).get("sesiones").get(0).get("resumen");

        assertMonto(turno, "ventasEfectivo", "0");
        assertMonto(turno, "efectivoFinal", "1000");
        assertThat(turno.get("ventas")).isEmpty();
    }

    @Test
    void conciliacion_elTurnoCoincideConElTotalDelDia() throws Exception {
        cargarDiaCompleto();

        JsonNode rango = pedir("/api/caja/resumen", hoy, hoy);
        JsonNode turno = rango.get("sesiones").get(0).get("resumen");
        JsonNode total = rango.get("total");

        assertThat(rango.get("sesiones")).hasSize(1);
        for (String campo : List.of("ventasEfectivo", "ventasTransferencia", "ventasTarjeta", "efectivoFinal",
                "totalDigital", "cajaTotalDelDia")) {
            assertThat(turno.get(campo).decimalValue()).as(campo).isEqualByComparingTo(total.get(campo).decimalValue());
        }
    }

    @Test
    void conciliacion_losReportesDeMostradorNoMezclanTrabajos() throws Exception {
        cargarDiaCompleto();

        JsonNode resumen = pedir("/api/reportes/resumen", hoy, hoy);
        assertMonto(resumen, "totalVendido", "100");
        assertThat(resumen.get("cantidadVentas").asLong()).isEqualTo(1);
        assertThat(resumen.get("unidadesVendidas").asLong()).isEqualTo(1);

        JsonNode porForma = pedir("/api/reportes/ventas-por-forma-pago", hoy, hoy);
        assertThat(porForma).hasSize(1);
        assertMonto(buscar(porForma, "medioPago", "EFECTIVO"), "totalFacturado", "100");

        JsonNode grafico = pedir("/api/reportes/medios-pago", hoy, hoy);
        assertThat(grafico).hasSize(1);
        assertMonto(grafico.get(0), "valor", "100");

        JsonNode ganadores = pedir("/api/reportes/productos-ganadores", hoy, hoy);
        assertThat(ganadores).hasSize(1);
        assertThat(ganadores.get(0).get("cantidadVendida").asLong()).isEqualTo(1);
        assertMonto(ganadores.get(0), "totalFacturado", "100");

        JsonNode marcas = pedir("/api/reportes/ventas-por-marca", hoy, hoy);
        assertThat(marcas).hasSize(1);
        assertThat(marcas.get(0).get("cantidadVendida").asLong()).isEqualTo(1);

        assertThat(pedir("/api/reportes/productos", hoy, hoy, "orden", "monto").get(0).get("unidades").asLong()).isEqualTo(1);
    }

    @Test
    void conciliacion_reporteDeTrabajosCierraConElDiaYElBalance() throws Exception {
        cargarDiaCompleto();

        JsonNode reporte = reporte(hoy, hoy);

        assertThat(reporte.get("cantidadTrabajos").asInt()).isEqualTo(3);
        assertMonto(reporte, "total", "5500");
        assertMonto(reporte, "manoDeObra", "5200");
        assertMonto(reporte, "articulos", "300");
        assertMonto(reporte, "paraTecnicos", "370");
        assertMonto(reporte, "paraNegocio", "5130");
        assertMonto(reporte, "costoArticulos", "180");
        assertMonto(reporte, "gananciaNegocio", "4950");

        JsonNode conTecnico = buscar(reporte.get("porTecnico"), "nombre", tecnico.getNombre());
        assertThat(conTecnico.get("cantidadTrabajos").asInt()).isEqualTo(2);
        assertMonto(conTecnico, "manoDeObra", "3700");
        assertMonto(conTecnico, "paraTecnico", "370");
        assertMonto(conTecnico, "paraNegocio", "3330");
        JsonNode sinTecnico = buscar(reporte.get("porTecnico"), "nombre", "Sin técnico");
        assertMonto(sinTecnico, "manoDeObra", "1500");
        assertMonto(sinTecnico, "paraTecnico", "0");
        assertMonto(sinTecnico, "paraNegocio", "1500");

        assertMonto(buscar(reporte.get("porMedioPago"), "etiqueta", "Efectivo"), "valor", "3200");
        assertMonto(buscar(reporte.get("porMedioPago"), "etiqueta", "Transferencia"), "valor", "1500");
        assertMonto(buscar(reporte.get("porMedioPago"), "etiqueta", "Tarjeta"), "valor", "800");
        assertMonto(buscar(reporte.get("porDia"), "etiqueta", hoy.toString()), "valor", "5500");
        assertThat(reporte.get("trabajos")).hasSize(3);

        // Total del día = mostrador + trabajos, y es lo mismo que los ingresos del balance.
        JsonNode dia = arqueo(hoy);
        JsonNode balance = balance(hoy);
        assertThat(dia.get("totalTrabajosDomicilio").decimalValue()).isEqualByComparingTo(reporte.get("total").decimalValue());
        assertThat(dia.get("totalDelDia").decimalValue()).isEqualByComparingTo(balance.get("ingresosPorVentas").decimalValue());
        assertThat(balance.get("ingresosPorTrabajos").decimalValue()).isEqualByComparingTo(reporte.get("total").decimalValue());
    }

    @Test
    void conciliacion_balanceYComisiones() throws Exception {
        cargarDiaCompleto();

        // Vendedor (5%): solo el margen de su venta de mostrador; de los trabajos no cobra nada.
        // Técnico (10%): solo la mano de obra que tuvo asignada (3000 + 700).
        JsonNode comisiones = pedir("/api/reportes/comisiones", hoy, hoy);
        assertMonto(buscar(comisiones, "idEmpleado", admin.getIdEmpleado().toString()), "gananciaGenerada", "40");
        assertMonto(buscar(comisiones, "idEmpleado", admin.getIdEmpleado().toString()), "comisionCalculada", "2");
        assertMonto(buscar(comisiones, "idEmpleado", tecnico.getIdEmpleado().toString()), "gananciaGenerada", "3700");
        assertMonto(buscar(comisiones, "idEmpleado", tecnico.getIdEmpleado().toString()), "comisionCalculada", "370");

        // Costo: 1 artículo de mostrador + 3 de trabajos, a 60 cada uno.
        JsonNode balance = balance(hoy);
        assertMonto(balance, "ingresosPorVentas", "5600");
        assertMonto(balance, "costoMercaderia", "240");
        assertMonto(balance, "comisionesPagadas", "372");
        assertMonto(balance, "gananciaNeta", "4988");
    }

    @Test
    void comision_alCincuentaPorCiento_seLlevaLaMitadDeLaManoDeObraYNadaDeLosArticulos() throws Exception {
        tecnico.setComision(new BigDecimal("50"));
        Map<String, Object> cuerpo = trabajo("EFECTIVO", true, List.of(articulo(1), manoDeObra(1000)));
        cuerpo.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        crear(cuerpo);

        JsonNode reporte = reporte(hoy, hoy);

        assertMonto(reporte, "total", "1100");
        assertMonto(reporte, "paraTecnicos", "500");
        assertMonto(reporte, "paraNegocio", "600");
        assertMonto(reporte, "gananciaNegocio", "540");
    }

    @Test
    void comision_cambiarlaNoAlteraLoYaCobrado_ySeAplicaALoQueSeCobreDespues() throws Exception {
        tecnico.setComision(new BigDecimal("50"));
        Map<String, Object> primero = trabajo("EFECTIVO", true, List.of(manoDeObra(1000)));
        primero.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        crear(primero);

        Map<String, Object> agendado = trabajo("EFECTIVO", false, List.of(manoDeObra(1000)));
        agendado.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        int idAgendado = crearId(agendado);

        guardarTecnico(tecnico.getIdEmpleado(), "Técnico renombrado", "20", 200);

        agendado.put("cerrar", true);
        actualizar(idAgendado, agendado);

        assertMonto(reporte(hoy, hoy), "paraTecnicos", "700");
        assertMonto(buscar(pedir("/api/reportes/comisiones", hoy, hoy), "idEmpleado", tecnico.getIdEmpleado().toString()),
            "comisionCalculada", "700");
    }

    @Test
    void comision_trabajoCobradoSinPorcentajeGuardado_usaElActualDelTecnico() throws Exception {
        tecnico.setComision(new BigDecimal("30"));
        Map<String, Object> cuerpo = trabajo("EFECTIVO", true, List.of(manoDeObra(1000)));
        cuerpo.put("idEmpleadoTecnico", tecnico.getIdEmpleado());
        int id = crearId(cuerpo);
        ventaRepo.findById(id).orElseThrow().setComisionTecnico(null);

        assertMonto(reporte(hoy, hoy), "paraTecnicos", "300");
    }

    @Test
    void reporte_noCuentaBorradoresNiAnulados() throws Exception {
        crear(trabajo("EFECTIVO", false, List.of(manoDeObra(9000))));
        int anulado = crearId(trabajo("EFECTIVO", false, List.of(manoDeObra(8000))));
        assertThat(anularDevolviendoStatus(anulado)).isEqualTo(200);

        JsonNode reporte = reporte(hoy, hoy);

        assertThat(reporte.get("cantidadTrabajos").asInt()).isEqualTo(0);
        assertMonto(reporte, "total", "0");
        assertThat(reporte.get("trabajos")).isEmpty();
    }

    @Test
    void reporte_trabajoViejoSinMedioDePago_figuraComoSinEspecificar() throws Exception {
        guardarCobradoEnHoraArgentina("DOMICILIO", LocalDateTime.of(2026, 3, 3, 12, 0), "500");
        Venta viejo = ventaRepo.findAll().get(0);
        viejo.setMedioPago(null);

        JsonNode reporte = reporte(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 3));

        assertMonto(buscar(reporte.get("porMedioPago"), "etiqueta", "Sin especificar"), "valor", "500");
    }

    @Test
    void reporte_rangoInvertido_seRechaza() throws Exception {
        mockMvc.perform(get("/api/reportes/trabajos-domicilio")
                .param("desde", hoy.toString())
                .param("hasta", hoy.minusDays(1).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unidadesVendidas_noCuentaLaManoDeObra() throws Exception {
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idEmpleado", admin.getIdEmpleado(),
                    "medioPago", "EFECTIVO",
                    "detalles", List.of(articulo(2), manoDeObra(500))))))
            .andExpect(status().isOk());

        assertThat(pedir("/api/reportes/resumen", hoy, hoy).get("unidadesVendidas").asLong()).isEqualTo(2);
    }

    @Test
    void tecnico_seCreaSoloConNombreYComision_yNoPuedeEntrarAlSistema() throws Exception {
        JsonNode creado = guardarTecnicoNuevo("Marcos", "50");

        assertThat(creado.get("rol").asText()).isEqualTo("TECNICO");
        assertMonto(creado, "comision", "50");
        Empleado guardado = empleadoRepo.findById(creado.get("idEmpleado").asInt()).orElseThrow();
        assertThat(guardado.getUsuario()).startsWith("tecnico-");
        assertThat(guardado.getPasswordHash()).isNotBlank();

        JsonNode actualizado = guardarTecnico(creado.get("idEmpleado").asInt(), "Marcos Díaz", "40", 200);
        assertThat(actualizado.get("nombre").asText()).isEqualTo("Marcos Díaz");
        assertMonto(actualizado, "comision", "40");
    }

    @Test
    void tecnico_validaLaComision_yNoSeEditaAOtroRolPorEstaVia() throws Exception {
        mockMvc.perform(post("/api/empleados/tecnicos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"nombre": "Sin comisión"}"""))
            .andExpect(status().isBadRequest());
        guardarTecnico(tecnico.getIdEmpleado(), "Técnico", "101", 400);
        guardarTecnico(admin.getIdEmpleado(), "No es técnico", "10", 400);

        assertThat(admin.getRol()).isEqualTo("ADMIN");
    }

    private JsonNode guardarTecnicoNuevo(String nombre, String comision) throws Exception {
        String respuesta = mockMvc.perform(post("/api/empleados/tecnicos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"nombre": "%s", "comision": %s}""".formatted(nombre, comision)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta);
    }

    private JsonNode guardarTecnico(int id, String nombre, String comision, int estadoEsperado) throws Exception {
        String respuesta = mockMvc.perform(put("/api/empleados/tecnicos/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                    {"nombre": "%s", "comision": %s}""".formatted(nombre, comision)))
            .andExpect(status().is(estadoEsperado))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta);
    }

    @Test
    void cobro_cuentaElDiaEnQueSeCobra_noElDiaEnQueSeCargo() throws Exception {
        abrirCaja(0);
        int id = crearId(trabajo("EFECTIVO", false, List.of(manoDeObra(3000))));
        LocalDate diaDeCarga = hoy.minusDays(5);
        Venta guardada = ventaRepo.findById(id).orElseThrow();
        guardada.setFecha(ZonaNegocio.inicioDelDia(diaDeCarga).plusHours(10));
        ventaRepo.save(guardada);

        actualizar(id, trabajo("EFECTIVO", true, List.of(manoDeObra(3000))));

        assertMonto(reporte(hoy, hoy), "total", "3000");
        assertMonto(arqueo(hoy), "totalTrabajosDomicilio", "3000");
        assertMonto(balance(hoy), "ingresosPorVentas", "3000");
        assertMonto(reporte(diaDeCarga, diaDeCarga), "total", "0");
        assertMonto(arqueo(diaDeCarga), "totalTrabajosDomicilio", "0");
        assertMonto(balance(diaDeCarga), "ingresosPorVentas", "0");
    }

    @Test
    void trabajo_deNoche_quedaEnElDiaDeArgentina() throws Exception {
        guardarCobradoEnHoraArgentina("DOMICILIO", LocalDateTime.of(2026, 3, 3, 22, 30), "500");
        guardarCobradoEnHoraArgentina("DOMICILIO", LocalDateTime.of(2026, 3, 4, 0, 10), "70");
        LocalDate dia3 = LocalDate.of(2026, 3, 3);
        LocalDate dia4 = LocalDate.of(2026, 3, 4);

        assertMonto(reporte(dia3, dia3), "total", "500");
        assertMonto(reporte(dia3, dia3).get("porDia").get(0), "valor", "500");
        assertMonto(arqueo(dia3), "totalTrabajosDomicilio", "500");
        assertMonto(balance(dia3), "ingresosPorVentas", "500");

        assertMonto(reporte(dia4, dia4), "total", "70");
        assertMonto(arqueo(dia4), "totalTrabajosDomicilio", "70");
        assertMonto(balance(dia4), "ingresosPorVentas", "70");
    }

    @Test
    void mostrador_deNoche_quedaEnElDiaDeArgentina_enArqueoYReportes() throws Exception {
        guardarCobradoEnHoraArgentina("MOSTRADOR", LocalDateTime.of(2026, 3, 3, 22, 30), "500");
        guardarCobradoEnHoraArgentina("MOSTRADOR", LocalDateTime.of(2026, 3, 4, 0, 10), "70");
        LocalDate dia3 = LocalDate.of(2026, 3, 3);
        LocalDate dia4 = LocalDate.of(2026, 3, 4);

        assertMonto(arqueo(dia3), "ventasEfectivo", "500");
        assertMonto(balance(dia3), "ingresosPorVentas", "500");
        assertMonto(pedir("/api/reportes/resumen", dia3, dia3), "totalVendido", "500");

        assertMonto(arqueo(dia4), "ventasEfectivo", "70");
        assertMonto(balance(dia4), "ingresosPorVentas", "70");
        assertMonto(pedir("/api/reportes/resumen", dia4, dia4), "totalVendido", "70");
    }

    private void guardarCobradoEnHoraArgentina(String tipoVenta, LocalDateTime fechaArgentina, String total) {
        Venta venta = new Venta();
        venta.setTipoVenta(tipoVenta);
        venta.setMedioPago("EFECTIVO");
        venta.setTotalVenta(new BigDecimal(total));
        venta.setFecha(fechaArgentina.atZone(ARGENTINA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        ventaRepo.save(venta);
    }

    @Test
    void agenda_unTrabajoFuturoFiguraEnElCalendarioSinTocarCajaNiReportes() throws Exception {
        abrirCaja(1000);
        LocalDate viernes = hoy.plusDays(7);
        Map<String, Object> cuerpo = trabajo("EFECTIVO", false, List.of(articulo(1), manoDeObra(4000)));
        cuerpo.put("fechaProgramada", viernes.toString());

        JsonNode creado = crear(cuerpo);

        assertThat(creado.get("fechaProgramada").asText()).isEqualTo(viernes.toString());
        JsonNode agenda = pedir("/api/ventas/trabajo-domicilio", viernes, viernes);
        assertThat(agenda).hasSize(1);
        assertThat(agenda.get(0).get("idVenta").asInt()).isEqualTo(creado.get("idVenta").asInt());
        assertThat(pedir("/api/ventas/trabajo-domicilio", hoy, hoy)).isEmpty();

        assertThat(stock()).isEqualTo(9);
        assertMonto(arqueo(hoy), "cajaTotalDelDia", "1000");
        assertMonto(arqueo(viernes), "ventasEfectivo", "0");
        assertMonto(balance(hoy), "ingresosPorVentas", "0");
        assertMonto(reporte(hoy, viernes), "total", "0");
    }

    @Test
    void agenda_cobrarConservaElDiaAgendado_yCuentaLaPlataHoy() throws Exception {
        LocalDate agendado = hoy.plusDays(3);
        Map<String, Object> cuerpo = trabajo("TRANSFERENCIA", false, List.of(manoDeObra(1200)));
        cuerpo.put("fechaProgramada", agendado.toString());
        int id = crearId(cuerpo);

        Map<String, Object> sinFecha = trabajo("TRANSFERENCIA", true, List.of(manoDeObra(1200)));
        actualizar(id, sinFecha);

        assertThat(pedir("/api/ventas/trabajo-domicilio", agendado, agendado)).hasSize(1);
        assertMonto(reporte(hoy, hoy), "total", "1200");
        assertMonto(reporte(agendado, agendado), "total", "0");
    }

    @Test
    void agenda_sinFechaVaAHoy() throws Exception {
        JsonNode creado = crear(trabajo("EFECTIVO", false, new ArrayList<>()));

        assertThat(creado.get("fechaProgramada").asText()).isEqualTo(hoy.toString());
    }

    @Test
    void cerrar_sinMedioDeCobroValido_seRechaza() throws Exception {
        assertThat(crearDevolviendoStatus(trabajo(null, true, List.of(manoDeObra(1000))))).isEqualTo(400);
        assertThat(crearDevolviendoStatus(trabajo("SALDO_CUENTA_CORRIENTE", true, List.of(manoDeObra(1000))))).isEqualTo(400);
    }

    @Test
    void borrador_noSePuedeMarcarComoCobradoSinCerrar() throws Exception {
        Map<String, Object> cuerpo = trabajo("EFECTIVO", false, List.of(manoDeObra(1000)));
        cuerpo.put("estadoTrabajo", "COBRADO");

        assertThat(crearDevolviendoStatus(cuerpo)).isEqualTo(400);
    }

    @Test
    void cobrado_noSeEdita_ySigueIgualEnReporteYStock() throws Exception {
        int id = crearId(trabajo("EFECTIVO", true, List.of(articulo(2), manoDeObra(1000))));
        assertThat(stock()).isEqualTo(8);

        assertThat(actualizarDevolviendoStatus(id, trabajo("EFECTIVO", false, List.of(articulo(1))))).isEqualTo(409);
        assertThat(actualizarDevolviendoStatus(id, trabajo("EFECTIVO", true, List.of(articulo(2), manoDeObra(1000)))))
            .isEqualTo(409);

        assertThat(stock()).isEqualTo(8);
        assertMonto(reporte(hoy, hoy), "total", "1200");
    }

    @Test
    void eliminar_unTrabajoCobrado_devuelveStockYSaleDeAgendaYReportes() throws Exception {
        int id = crearId(trabajo("EFECTIVO", true, List.of(articulo(2), manoDeObra(1000))));
        assertThat(stock()).isEqualTo(8);
        assertThat(pedir("/api/ventas/trabajo-domicilio", hoy, hoy)).hasSize(1);

        assertThat(anularDevolviendoStatus(id)).isEqualTo(200);

        assertThat(stock()).isEqualTo(10);
        assertThat(pedir("/api/ventas/trabajo-domicilio", hoy, hoy)).isEmpty();
        assertMonto(reporte(hoy, hoy), "total", "0");
        assertMonto(arqueo(hoy), "totalTrabajosDomicilio", "0");
        assertMonto(arqueo(hoy), "totalDelDia", "0");
        assertMonto(balance(hoy), "ingresosPorVentas", "0");
        assertMonto(balance(hoy), "costoMercaderia", "0");
        assertThat(pedir("/api/reportes/comisiones", hoy, hoy)).isEmpty();
        // Sigue auditable: no se borra la fila.
        assertThat(ventaRepo.findById(id).orElseThrow().getEstado()).isEqualTo("ANULADA");
    }

    @Test
    void eliminar_conFacturaEmitida_seRechazaSinTocarNada() throws Exception {
        int id = crearId(trabajo("EFECTIVO", true, List.of(articulo(2), manoDeObra(1000))));
        guardarFactura(id, "EMITIDA");

        assertThat(anularDevolviendoStatus(id)).isEqualTo(409);

        assertThat(stock()).isEqualTo(8);
        assertMonto(reporte(hoy, hoy), "total", "1200");
    }

    @Test
    void eliminar_conFacturaQueDioError_sePuede() throws Exception {
        int id = crearId(trabajo("EFECTIVO", true, List.of(manoDeObra(1000))));
        guardarFactura(id, "ERROR");

        assertThat(anularDevolviendoStatus(id)).isEqualTo(200);
    }

    @Test
    void eliminar_conNotaDeCredito_seRechaza() throws Exception {
        int id = crearId(trabajo("EFECTIVO", true, List.of(articulo(1), manoDeObra(1000))));
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente de prueba");
        cliente = clienteRepo.save(cliente);
        NotaCredito nota = new NotaCredito();
        nota.setVentaOriginal(ventaRepo.findById(id).orElseThrow());
        nota.setCliente(cliente);
        nota.setMotivo("ERROR_TIPEO");
        nota.setAlcance("PARCIAL");
        nota.setMontoTotal(new BigDecimal("100"));
        notaCreditoRepo.save(nota);

        assertThat(anularDevolviendoStatus(id)).isEqualTo(409);

        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void eliminar_unaVentaDeMostrador_seRechaza() throws Exception {
        Venta mostrador = new Venta();
        mostrador.setMedioPago("EFECTIVO");

        assertThat(anularDevolviendoStatus(ventaRepo.save(mostrador).getIdVenta())).isEqualTo(400);
    }

    private void guardarFactura(int idVenta, String estado) {
        FacturaFiscal factura = new FacturaFiscal();
        factura.setVenta(ventaRepo.findById(idVenta).orElseThrow());
        factura.setPuntoVenta(1);
        factura.setTipoComprobante(11);
        factura.setClienteDocTipo(99);
        factura.setEstado(estado);
        facturaRepo.save(factura);
    }

    @Test
    void anulado_noSeVuelveAAnular_paraNoDevolverDosVecesElStock() throws Exception {
        int id = crearId(trabajo("EFECTIVO", false, List.of(articulo(3))));
        assertThat(anularDevolviendoStatus(id)).isEqualTo(200);

        assertThat(anularDevolviendoStatus(id)).isEqualTo(409);

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void vendedor_noVeLaAgendaNiElReporteNiTecnicos() throws Exception {
        int id = crearId(trabajo("EFECTIVO", false, List.of()));
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/reportes/trabajos-domicilio")
                .param("desde", hoy.toString())
                .param("hasta", hoy.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/empleados/tecnicos")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content("""
                    {"nombre": "Marcos", "comision": 50}"""))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ventas/trabajo-domicilio")
                .param("desde", hoy.toString())
                .param("hasta", hoy.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/anular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
    }
}
