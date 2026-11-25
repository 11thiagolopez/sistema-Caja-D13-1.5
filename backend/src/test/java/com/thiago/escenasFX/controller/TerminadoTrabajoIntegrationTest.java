package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

/**
 * "Terminado" según el dueño: el del técnico es solo un aviso; el del jefe bloquea el trabajo
 * (ni datos, ni notas, ni fotos, ni eliminar) hasta que se cobra o se reabre. Y el stock de los
 * artículos se descuenta una sola vez en todo el recorrido.
 */
class TerminadoTrabajoIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired
    private ProductoRepository productoRepo;

    private Empleado admin;
    private Empleado fabian;
    private String tokenAdmin;
    private String tokenFabian;
    private Producto cerradura;

    @BeforeEach
    void prepararDatos() throws Exception {
        admin = crearEmpleado("admin1", "clave123", "ADMIN", "admin1@test.com");
        fabian = crearEmpleado("fabian", "clave123", "TECNICO", null);
        tokenAdmin = login("admin1", "clave123");
        tokenFabian = login("fabian", "clave123");
        Producto p = new Producto();
        p.setDescripcion("Cerradura de prueba");
        p.setStockActual(10);
        p.setPrecioVenta(new BigDecimal("18500"));
        p.setPrecioCompra(new BigDecimal("11000"));
        cerradura = productoRepo.save(p);
    }

    private Map<String, Object> trabajo(String estado) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEmpleado", admin.getIdEmpleado());
        cuerpo.put("idEmpleadoTecnico", fabian.getIdEmpleado());
        cuerpo.put("clienteNombre", "Carlos Ferreyra");
        cuerpo.put("direccionTrabajo", "Soldados 750");
        cuerpo.put("estadoTrabajo", estado);
        cuerpo.put("medioPago", "EFECTIVO");
        cuerpo.put("cerrar", false);
        // Precio distinto al de mostrador (18500): lo que pidió el dueño para los materiales.
        cuerpo.put("detalles", List.of(Map.of("idProducto", cerradura.getIdProducto(), "tipo", "ARTICULO",
            "cantidad", 2, "precioUnitario", 22000)));
        return cuerpo;
    }

    private int crear() throws Exception {
        String r = mockMvc.perform(post("/api/ventas/trabajo-domicilio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content(objectMapper.writeValueAsString(trabajo("AGENDADO"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(r).get("idVenta").asInt();
    }

    private org.springframework.test.web.servlet.ResultActions guardar(int id, String estado) throws Exception {
        return mockMvc.perform(put("/api/ventas/trabajo-domicilio/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
            .content(objectMapper.writeValueAsString(trabajo(estado))));
    }

    private int stock() {
        return productoRepo.findById(cerradura.getIdProducto()).orElseThrow().getStockActual();
    }

    @Test
    void recorridoCompleto_avisoDelTecnico_terminadoDelJefe_bloqueo_reabrir_cobrar() throws Exception {
        int id = crear();
        assertThat(stock()).isEqualTo(8);

        // El técnico avisa que terminó: no bloquea nada, el jefe puede seguir editando.
        mockMvc.perform(post("/api/mis-trabajos/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"estado\": \"TERMINADO_TECNICO\"}"))
            .andExpect(status().isOk());
        guardar(id, "TERMINADO_TECNICO").andExpect(status().isOk());

        // El técnico no puede fijar el Terminado del jefe.
        mockMvc.perform(post("/api/mis-trabajos/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"estado\": \"TERMINADO\"}"))
            .andExpect(status().isBadRequest());

        // El jefe lo marca Terminado: queda bloqueado para todos.
        guardar(id, "TERMINADO").andExpect(status().isOk()).andExpect(jsonPath("$.estadoTrabajo").value("TERMINADO"));
        guardar(id, "TERMINADO").andExpect(status().isConflict());
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/anular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/mis-trabajos/" + id + "/notas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"texto\": \"me olvidé algo\"}"))
            .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/mis-trabajos/" + id + "/fotos")
                .file(new MockMultipartFile("fotos", "f.jpg", "image/jpeg", JPEG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/ventas/trabajo-domicilio/" + id + "/fotos")
                .file(new MockMultipartFile("fotos", "f.jpg", "image/jpeg", JPEG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isConflict());

        // Reabrir: vuelve al aviso del técnico y se puede editar de nuevo.
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/reabrir")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoTrabajo").value("TERMINADO_TECNICO"));
        guardar(id, "TERMINADO").andExpect(status().isOk());

        // Cobrar con los ítems tal cual: 2 x 22000, a la caja aparte (sin sesión de caja).
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/cobrar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"medioPago\": \"TRANSFERENCIA\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
            .andExpect(jsonPath("$.estadoTrabajo").value("CERRADO_Y_COBRADO"))
            .andExpect(jsonPath("$.medioPago").value("TRANSFERENCIA"))
            .andExpect(jsonPath("$.totalVenta").value(44000.0));

        // Todas esas vueltas (guardar, terminar, reabrir, cobrar) descontaron el stock una sola vez.
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void cobrar_unTrabajoQueNoEstaTerminado_rechaza() throws Exception {
        int id = crear();

        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/cobrar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .content("{\"medioPago\": \"EFECTIVO\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void reabrirYCobrar_sonSoloDelJefe() throws Exception {
        int id = crear();
        guardar(id, "TERMINADO").andExpect(status().isOk());

        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/reabrir")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ventas/trabajo-domicilio/" + id + "/cobrar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFabian)
                .content("{\"medioPago\": \"EFECTIVO\"}"))
            .andExpect(status().isForbidden());
    }
}
