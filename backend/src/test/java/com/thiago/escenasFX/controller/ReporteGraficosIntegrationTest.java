package com.thiago.escenasFX.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Corre las consultas reales contra H2. Las fechas se arman en hora de Argentina y se guardan en la
 * zona de la JVM, igual que LocalDateTime.now() en producción. El comportamiento de AT TIME ZONE en
 * Postgres no se ejercita acá.
 */
class ReporteGraficosIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private VentaRepository ventaRepo;

    @Autowired
    private ProductoRepository productoRepo;

    private String tokenAdmin;

    @BeforeEach
    void loguearAdmin() throws Exception {
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        tokenAdmin = login("admin1", "clave123");
    }

    private Producto producto(String descripcion, String marca) {
        Producto p = new Producto();
        p.setDescripcion(descripcion);
        p.setMarca(marca);
        return productoRepo.save(p);
    }

    private Venta venta(LocalDateTime fechaArgentina, String medioPago, String descuento) {
        Venta v = new Venta();
        v.setFecha(fechaArgentina.atZone(ARGENTINA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        v.setMedioPago(medioPago);
        v.setDescuento(new BigDecimal(descuento));
        return v;
    }

    private void agregarLinea(Venta v, Producto p, int cantidad, String precio) {
        DetalleVenta d = new DetalleVenta();
        d.setVenta(v);
        d.setProducto(p);
        d.setDescripcion(p != null ? p.getDescripcion() : "Trabajo manual");
        d.setCantidad(cantidad);
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setSubtotal(new BigDecimal(precio).multiply(BigDecimal.valueOf(cantidad)));
        v.getDetalles().add(d);
    }

    private Venta guardar(Venta v) {
        BigDecimal bruto = v.getDetalles().stream()
            .map(DetalleVenta::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        v.setTotalVenta(bruto.subtract(v.getDescuento()));
        return ventaRepo.save(v);
    }

    private ResultActions pedir(String ruta, String desde, String hasta) throws Exception {
        return mockMvc.perform(get("/api/reportes/" + ruta)
            .param("desde", desde).param("hasta", hasta)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin));
    }

    @Test
    void resumen_sumaSoloConfirmadasDelRango() throws Exception {
        Producto llave = producto("Llave", "KALOP");

        Venta a = venta(LocalDateTime.of(2026, 3, 2, 10, 0), "EFECTIVO", "0");
        agregarLinea(a, llave, 2, "100");
        guardar(a);

        Venta b = venta(LocalDateTime.of(2026, 3, 3, 10, 0), "TRANSFERENCIA", "50");
        agregarLinea(b, llave, 1, "150");
        agregarLinea(b, null, 1, "50");
        guardar(b);

        Venta pendiente = venta(LocalDateTime.of(2026, 3, 3, 11, 0), "EFECTIVO", "0");
        agregarLinea(pendiente, llave, 5, "100");
        pendiente.setEstado("PENDIENTE_AUTORIZACION");
        guardar(pendiente);

        Venta fueraDeRango = venta(LocalDateTime.of(2026, 3, 5, 10, 0), "EFECTIVO", "0");
        agregarLinea(fueraDeRango, llave, 5, "100");
        guardar(fueraDeRango);

        ResultActions respuesta = pedir("resumen", "2026-03-02", "2026-03-03");
        System.out.println("GET /api/reportes/resumen -> " + respuesta.andReturn().getResponse().getContentAsString());

        respuesta.andExpect(status().isOk())
            .andExpect(jsonPath("$.totalVendido").value(350.00))
            .andExpect(jsonPath("$.cantidadVentas").value(2))
            .andExpect(jsonPath("$.ticketPromedio").value(175.00))
            .andExpect(jsonPath("$.unidadesVendidas").value(4));
    }

    @Test
    void resumen_sinVentas_devuelveCeros() throws Exception {
        pedir("resumen", "2026-03-02", "2026-03-03")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalVendido").value(0))
            .andExpect(jsonPath("$.cantidadVentas").value(0))
            .andExpect(jsonPath("$.ticketPromedio").value(0))
            .andExpect(jsonPath("$.unidadesVendidas").value(0));
    }

    @Test
    void resumen_rangoInvertido_devuelve400() throws Exception {
        pedir("resumen", "2026-03-03", "2026-03-02").andExpect(status().isBadRequest());
    }

    @Test
    void ventaDeNoche_cuentaEnElDiaDeArgentina() throws Exception {
        Venta deNoche = venta(LocalDateTime.of(2026, 3, 2, 23, 30), "EFECTIVO", "0");
        agregarLinea(deNoche, producto("Llave", "KALOP"), 1, "100");
        guardar(deNoche);

        pedir("resumen", "2026-03-03", "2026-03-03").andExpect(jsonPath("$.cantidadVentas").value(0));
        pedir("resumen", "2026-03-02", "2026-03-02").andExpect(jsonPath("$.cantidadVentas").value(1));

        // H2 pasa de timestamptz a date con la zona de la sesión, no con la del valor como Postgres:
        // este tramo solo es comparable si la JVM corre en hora de Argentina.
        assumeTrue(ARGENTINA.getRules().getOffset(Instant.parse("2026-03-02T12:00:00Z"))
            .equals(ZoneId.systemDefault().getRules().getOffset(Instant.parse("2026-03-02T12:00:00Z"))));
        pedir("ventas-por-dia", "2026-03-02", "2026-03-03")
            .andExpect(jsonPath("$.puntos[0].etiqueta").value("2026-03-02"))
            .andExpect(jsonPath("$.puntos[0].valor").value(100.00))
            .andExpect(jsonPath("$.puntos[1].valor").value(0));
    }

    @Test
    void marcas_repartenElDescuentoYUnificanMayusculasYVacias() throws Exception {
        Producto kalop = producto("Llave", "KALOP");
        Producto kalopMinuscula = producto("Candado", " kalop ");
        Producto cambre = producto("Tecla", "Cambre");
        Producto sinMarca = producto("Tornillo", null);
        Producto marcaEnBlanco = producto("Tuerca", "  ");

        // Bruto 400 con descuento 100: cada línea cobra el 75% de su subtotal.
        Venta conDescuento = venta(LocalDateTime.of(2026, 3, 2, 10, 0), "EFECTIVO", "100");
        agregarLinea(conDescuento, kalop, 1, "100");
        agregarLinea(conDescuento, kalopMinuscula, 1, "100");
        agregarLinea(conDescuento, cambre, 1, "200");
        agregarLinea(conDescuento, null, 0, "0");
        guardar(conDescuento);

        Venta otra = venta(LocalDateTime.of(2026, 3, 2, 11, 0), "EFECTIVO", "0");
        agregarLinea(otra, sinMarca, 7, "1");
        agregarLinea(otra, marcaEnBlanco, 3, "1");
        guardar(otra);

        ResultActions porMonto = pedir("marcas", "2026-03-02", "2026-03-02");
        System.out.println("GET /api/reportes/marcas -> " + porMonto.andReturn().getResponse().getContentAsString());

        porMonto.andExpect(jsonPath("$.mejores.length()").value(3))
            .andExpect(jsonPath("$.mejores[0].etiqueta").value("CAMBRE"))
            .andExpect(jsonPath("$.mejores[0].valor").value(150.00))
            .andExpect(jsonPath("$.mejores[1].etiqueta").value("KALOP"))
            .andExpect(jsonPath("$.mejores[1].monto").value(150.00))
            .andExpect(jsonPath("$.mejores[1].unidades").value(2))
            .andExpect(jsonPath("$.mejores[2].etiqueta").value("Sin marca"))
            .andExpect(jsonPath("$.mejores[2].unidades").value(10))
            .andExpect(jsonPath("$.peores.length()").value(2));

        mockMvc.perform(get("/api/reportes/marcas")
                .param("desde", "2026-03-02").param("hasta", "2026-03-02").param("orden", "unidades")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.mejores[0].etiqueta").value("Sin marca"))
            .andExpect(jsonPath("$.mejores[0].valor").value(10));
    }

    @Test
    void productos_ordenInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/api/reportes/productos")
                .param("desde", "2026-03-02").param("hasta", "2026-03-02").param("orden", "cualquiera")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isBadRequest());
    }

    @Test
    void productos_ordenaSegunElPedidoYExcluyeManuales() throws Exception {
        Producto caro = producto("Cerradura", "KALOP");
        Producto barato = producto("Llave", "KALOP");

        Venta v = venta(LocalDateTime.of(2026, 3, 2, 10, 0), "EFECTIVO", "0");
        agregarLinea(v, caro, 1, "1000");
        agregarLinea(v, barato, 5, "10");
        agregarLinea(v, null, 1, "5000");
        guardar(v);

        pedir("productos", "2026-03-02", "2026-03-02")
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].etiqueta").value("Cerradura"))
            .andExpect(jsonPath("$[0].valor").value(1000.00));

        mockMvc.perform(get("/api/reportes/productos")
                .param("desde", "2026-03-02").param("hasta", "2026-03-02").param("orden", "unidades")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$[0].etiqueta").value("Llave"))
            .andExpect(jsonPath("$[0].valor").value(5))
            .andExpect(jsonPath("$[0].monto").value(50.00));
    }

    @Test
    void ventasPorDia_completaConCerosYElPeorDiaIgnoraLosDiasSinVentas() throws Exception {
        Producto llave = producto("Llave", "KALOP");

        Venta lunes = venta(LocalDateTime.of(2026, 3, 2, 10, 0), "EFECTIVO", "0");
        agregarLinea(lunes, llave, 1, "500");
        guardar(lunes);

        Venta miercoles = venta(LocalDateTime.of(2026, 3, 4, 10, 0), "EFECTIVO", "0");
        agregarLinea(miercoles, llave, 1, "200");
        guardar(miercoles);

        pedir("ventas-por-dia", "2026-03-02", "2026-03-05")
            .andExpect(jsonPath("$.puntos.length()").value(4))
            .andExpect(jsonPath("$.puntos[1].etiqueta").value("2026-03-03"))
            .andExpect(jsonPath("$.puntos[1].valor").value(0))
            .andExpect(jsonPath("$.mejorDia.etiqueta").value("2026-03-02"))
            .andExpect(jsonPath("$.peorDia.etiqueta").value("2026-03-04"));
    }

    @Test
    void ventasPorDia_sinVentas_noTieneMejorNiPeorDia() throws Exception {
        pedir("ventas-por-dia", "2026-03-02", "2026-03-03")
            .andExpect(jsonPath("$.puntos.length()").value(2))
            .andExpect(jsonPath("$.mejorDia").value(nullValue()))
            .andExpect(jsonPath("$.peorDia").value(nullValue()));
    }

    @Test
    void ventasPorDiaSemana_promediaContandoLosDiasSinVentas() throws Exception {
        // 2 y 9 de marzo de 2026 son lunes: 300 en dos lunes (uno sin ventas) promedia 150.
        LocalDate primerLunes = LocalDate.of(2026, 3, 2);
        Venta v = venta(primerLunes.atTime(10, 0), "EFECTIVO", "0");
        agregarLinea(v, producto("Llave", "KALOP"), 1, "300");
        guardar(v);

        pedir("ventas-por-dia-semana", "2026-03-02", "2026-03-09")
            .andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$[0].etiqueta").value("Lunes"))
            .andExpect(jsonPath("$[0].valor").value(150.00))
            .andExpect(jsonPath("$[6].etiqueta").value("Domingo"))
            .andExpect(jsonPath("$[6].valor").value(0));
    }

    @Test
    void mediosPago_separaLoPagadoConSaldo() throws Exception {
        Producto llave = producto("Llave", "KALOP");

        Venta parcial = venta(LocalDateTime.of(2026, 3, 2, 10, 0), "EFECTIVO", "0");
        agregarLinea(parcial, llave, 1, "1000");
        parcial.setMontoSaldoAplicado(new BigDecimal("400"));
        guardar(parcial);

        Venta total = venta(LocalDateTime.of(2026, 3, 2, 11, 0), "SALDO_CUENTA_CORRIENTE", "0");
        agregarLinea(total, llave, 1, "100");
        total.setMontoSaldoAplicado(new BigDecimal("100"));
        guardar(total);

        Venta sinMedio = venta(LocalDateTime.of(2026, 3, 2, 12, 0), null, "0");
        agregarLinea(sinMedio, llave, 1, "50");
        guardar(sinMedio);

        pedir("medios-pago", "2026-03-02", "2026-03-02")
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].etiqueta").value("Efectivo"))
            .andExpect(jsonPath("$[0].valor").value(600.00))
            .andExpect(jsonPath("$[1].etiqueta").value("Saldo cuenta corriente"))
            .andExpect(jsonPath("$[1].valor").value(500.00))
            .andExpect(jsonPath("$[2].etiqueta").value("Sin especificar"))
            .andExpect(jsonPath("$[2].valor").value(50.00));
    }

    @Test
    void graficos_comoVendedor_devuelve403() throws Exception {
        crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        String tokenVendedor = login("vendedor1", "clave123");

        mockMvc.perform(get("/api/reportes/resumen")
                .param("desde", "2026-03-02").param("hasta", "2026-03-02")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor))
            .andExpect(status().isForbidden());
    }
}
