package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

/**
 * Ejercita el flujo completo de venta (controller -> VentaService -> repositorios -> H2 real,
 * más autenticación JWT real) incluyendo el camino con descuento manual + OTP, capturando el
 * código del email mockeado en vez de simularlo.
 */
class VentaControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductoRepository productoRepo;

    private static final Pattern PATRON_CODIGO = Pattern.compile("Código de confirmación: (\\d{6})");

    private Producto crearProducto(int stockInicial) {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(stockInicial);
        p.setPrecioVenta(new BigDecimal("100"));
        return productoRepo.save(p);
    }

    private String extraerCodigoOtpDelUltimoEmail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        Matcher m = PATRON_CODIGO.matcher(captor.getValue().getText());
        assertThat(m.find()).as("el email debe contener el código OTP de 6 dígitos").isTrue();
        return m.group(1);
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
    void registrarVentaConDescuento_quedaPendienteYSeConfirmaConElOtpDelEmail() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 2, "precioUnitario": 100}],
                "descuento": 50,
                "motivoDescuento": "Cliente frecuente"
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        String respuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE_AUTORIZACION"))
            .andReturn().getResponse().getContentAsString();

        Integer idVenta = objectMapper.readTree(respuesta).get("idVenta").asInt();
        String codigo = extraerCodigoOtpDelUltimoEmail();

        String tokenAdmin = login("admin1", "clave123");
        String confirmarBody = """
            {"idVenta": %d, "codigo": "%s"}
            """.formatted(idVenta, codigo);

        mockMvc.perform(post("/api/ventas/descuento/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(confirmarBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    void confirmarDescuento_codigoIncorrecto_devuelve401() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}],
                "descuento": 10,
                "motivoDescuento": "Motivo"
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        String respuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Integer idVenta = objectMapper.readTree(respuesta).get("idVenta").asInt();

        String tokenAdmin = login("admin1", "clave123");
        String confirmarBody = """
            {"idVenta": %d, "codigo": "000000"}
            """.formatted(idVenta);

        mockMvc.perform(post("/api/ventas/descuento/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(confirmarBody))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Flujo real: el VENDEDOR registra la venta con descuento, el OTP le llega por email al
     * ADMIN (nunca al VENDEDOR), pero es el VENDEDOR quien está frente al cliente y termina la
     * venta una vez que el ADMIN le pasa el código. El control de seguridad está en quién recibe
     * el email, no en qué rol aprieta "Confirmar" — por eso VENDEDOR puede confirmar su propia
     * venta si tiene el código correcto.
     */
    @Test
    void confirmarDescuento_comoVendedor_funciona() throws Exception {
        Empleado vendedor = crearEmpleado("vendedor1", "clave123", "VENDEDOR", null);
        crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        Producto producto = crearProducto(10);
        String tokenVendedor = login("vendedor1", "clave123");

        String body = """
            {
                "idEmpleado": %d,
                "medioPago": "EFECTIVO",
                "detalles": [{"idProducto": %d, "cantidad": 1, "precioUnitario": 100}],
                "descuento": 10,
                "motivoDescuento": "Motivo"
            }
            """.formatted(vendedor.getIdEmpleado(), producto.getIdProducto());

        String respuesta = mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE_AUTORIZACION"))
            .andReturn().getResponse().getContentAsString();

        Integer idVenta = objectMapper.readTree(respuesta).get("idVenta").asInt();
        String codigo = extraerCodigoOtpDelUltimoEmail();

        mockMvc.perform(post("/api/ventas/descuento/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVendedor)
                .content("""
                    {"idVenta": %d, "codigo": "%s"}""".formatted(idVenta, codigo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
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
