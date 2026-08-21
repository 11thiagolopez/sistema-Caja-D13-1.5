package com.thiago.escenasFX.controller;

import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.AfipFacturacionService;
import com.thiago.escenasFX.service.CotizacionApiClient;
import com.thiago.escenasFX.service.EmailService;

/**
 * Base para los tests de integración HTTP: levanta el contexto Spring completo (controller ->
 * service -> repository -> H2 real) con seguridad JWT activa, y mockea el envío de emails
 * (EmailService, que le pega a la API de Resend) para no depender de un servicio de mail real en
 * los flujos de OTP.
 *
 * Cada test corre dentro de una transacción que se revierte al final (@Transactional), así que
 * los datos que crea un test no afectan a los demás sin necesidad de limpieza manual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EmpleadoRepository empleadoRepo;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockBean
    protected EmailService emailService;

    // Dolarización: evita que abrir caja le pegue a las APIs públicas de cotización durante los
    // tests (red real, flaky, lenta). Con un valor por defecto fijo, cualquier test que abra caja
    // obtiene una cotización determinística sin tener que mockear esto uno por uno.
    @MockBean
    protected CotizacionApiClient cotizacionApiClient;

    // Facturación fiscal: evita que "Facturar" le pegue a ARCA de verdad durante los tests (además
    // de que no hay certificado configurado en el entorno de test). Mockeado en el nivel más alto
    // (AfipFacturacionService, no AfipAuthService) para que FacturaFiscalService corra su lógica
    // real de todos modos.
    @MockBean
    protected AfipFacturacionService afipFacturacionService;

    @BeforeEach
    void configurarCotizacionPorDefecto() {
        lenient().when(cotizacionApiClient.consultarPrimaria()).thenReturn(Optional.of(new BigDecimal("1000")));
        lenient().when(cotizacionApiClient.consultarSecundaria()).thenReturn(Optional.of(new BigDecimal("1000")));
    }

    @BeforeEach
    void configurarFacturacionPorDefecto() {
        lenient().when(afipFacturacionService.emitirFacturaC(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new AfipFacturacionService.ResultadoCae(true, 1, "12345678901234",
                java.time.LocalDate.now().plusDays(10), null));
    }

    protected Empleado crearEmpleado(String usuario, String passwordPlano, String rol, String email) {
        Empleado empleado = new Empleado();
        empleado.setNombre("Test " + usuario);
        empleado.setUsuario(usuario);
        empleado.setPasswordHash(passwordEncoder.encode(passwordPlano));
        empleado.setRol(rol);
        empleado.setEmail(email);
        return empleadoRepo.save(empleado);
    }

    /**
     * Hace login real contra AuthController y devuelve el JWT emitido, para ejercitar el flujo de
     * autenticación completo en vez de simularlo con @WithMockUser.
     */
    protected String login(String usuario, String passwordPlano) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(usuario, passwordPlano));

        String respuesta = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(respuesta);
        return json.get("token").asText();
    }

    private record LoginPayload(String usuario, String password) {
    }
}
