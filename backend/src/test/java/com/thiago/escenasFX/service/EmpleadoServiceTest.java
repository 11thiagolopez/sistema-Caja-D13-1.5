package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thiago.escenasFX.dto.EmpleadoRequest;
import com.thiago.escenasFX.dto.EmpleadoUpdateRequest;
import com.thiago.escenasFX.dto.TecnicoRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    @Mock
    private EmpleadoRepository empleadoRepo;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmpleadoService empleadoService;

    @BeforeEach
    void configuracionPorDefecto() {
        lenient().when(empleadoRepo.findByUsuario(anyString())).thenReturn(Optional.empty());
        lenient().when(empleadoRepo.save(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hash");
    }

    private EmpleadoRequest request(String rol, BigDecimal comision) {
        EmpleadoRequest req = new EmpleadoRequest();
        req.setNombre("Empleado de prueba");
        req.setUsuario("prueba");
        req.setPassword("clave123");
        req.setRol(rol);
        req.setComision(comision);
        return req;
    }

    @Test
    void crear_tecnicoConComision_laConserva() {
        Empleado guardado = empleadoService.crear(request("TECNICO", new BigDecimal("15")));

        assertThat(guardado.getComision()).isEqualByComparingTo("15");
    }

    @Test
    void crear_vendedorConComisionEnviada_seIgnoraYQuedaNull() {
        // Aunque el request traiga una comisión (ej. un cliente desactualizado o manipulado), un
        // VENDEDOR tiene sueldo fijo y nunca debe quedar con comisión cargada.
        Empleado guardado = empleadoService.crear(request("VENDEDOR", new BigDecimal("15")));

        assertThat(guardado.getComision()).isNull();
    }

    @Test
    void crear_adminConComisionEnviada_seIgnoraYQuedaNull() {
        Empleado guardado = empleadoService.crear(request("ADMIN", new BigDecimal("15")));

        assertThat(guardado.getComision()).isNull();
    }

    @Test
    void actualizar_cambiaDeTecnicoAVendedor_pierdeLaComision() {
        Empleado existente = new Empleado();
        existente.setIdEmpleado(1);
        existente.setRol("TECNICO");
        existente.setComision(new BigDecimal("20"));
        when(empleadoRepo.findById(1)).thenReturn(Optional.of(existente));

        EmpleadoUpdateRequest req = new EmpleadoUpdateRequest();
        req.setNombre("Empleado de prueba");
        req.setUsuario("prueba");
        req.setRol("VENDEDOR");
        req.setComision(new BigDecimal("20"));

        Empleado actualizado = empleadoService.actualizar(1, req);

        assertThat(actualizado.getComision()).isNull();
    }

    private TecnicoRequest tecnicoRequest(String usuario, String password) {
        TecnicoRequest req = new TecnicoRequest();
        req.setNombre("Fabián");
        req.setComision(new BigDecimal("30"));
        req.setUsuario(usuario);
        req.setPassword(password);
        return req;
    }

    @Test
    void crearTecnico_sinUsuario_quedaSinAccesoAlSistema() {
        Empleado tecnico = empleadoService.crearTecnico(tecnicoRequest(null, null));

        assertThat(tecnico.getUsuario()).startsWith("tecnico-");
        assertThat(tecnico.getRol()).isEqualTo("TECNICO");
    }

    @Test
    void crearTecnico_conUsuarioYPassword_quedaConAccesoReal() {
        Empleado tecnico = empleadoService.crearTecnico(tecnicoRequest("fabian", "clave123"));

        assertThat(tecnico.getUsuario()).isEqualTo("fabian");
        assertThat(tecnico.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void crearTecnico_conUsuarioYaUsado_rechaza() {
        when(empleadoRepo.findByUsuario("fabian")).thenReturn(Optional.of(new Empleado()));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> empleadoService.crearTecnico(tecnicoRequest("fabian", "clave123")));
    }

    @Test
    void actualizarTecnico_sinUsuarioEnElRequest_noLeTocaElAccesoQueYaTenia() {
        Empleado existente = new Empleado();
        existente.setIdEmpleado(5);
        existente.setRol("TECNICO");
        existente.setUsuario("fabian");
        existente.setPasswordHash("hash-viejo");
        when(empleadoRepo.findById(5)).thenReturn(Optional.of(existente));

        Empleado actualizado = empleadoService.actualizarTecnico(5, tecnicoRequest(null, null));

        assertThat(actualizado.getUsuario()).isEqualTo("fabian");
        assertThat(actualizado.getPasswordHash()).isEqualTo("hash-viejo");
    }

    @Test
    void actualizarTecnico_deUnoQueNoEsTecnico_rechaza() {
        Empleado existente = new Empleado();
        existente.setIdEmpleado(5);
        existente.setRol("VENDEDOR");
        when(empleadoRepo.findById(5)).thenReturn(Optional.of(existente));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> empleadoService.actualizarTecnico(5, tecnicoRequest(null, null)));
    }

    @Test
    void crearTecnico_conUsuarioSinPassword_rechaza() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> empleadoService.crearTecnico(tecnicoRequest("fabian", " ")));
    }

    @Test
    void crearTecnico_conUsuarioDelPrefijoReservado_rechaza() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> empleadoService.crearTecnico(tecnicoRequest("tecnico-fabian", "clave123")));
    }

    private Empleado tecnicoSinAcceso() {
        Empleado existente = new Empleado();
        existente.setIdEmpleado(5);
        existente.setRol("TECNICO");
        existente.setUsuario("tecnico-1234");
        existente.setPasswordHash("hash-al-azar");
        when(empleadoRepo.findById(5)).thenReturn(Optional.of(existente));
        return existente;
    }

    @Test
    void actualizarTecnico_darleAccesoSinPassword_rechaza() {
        tecnicoSinAcceso();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> empleadoService.actualizarTecnico(5, tecnicoRequest("fabian", null)));
    }

    @Test
    void actualizarTecnico_darleAccesoConPassword_quedaConAccesoReal() {
        tecnicoSinAcceso();

        Empleado actualizado = empleadoService.actualizarTecnico(5, tecnicoRequest("fabian", "clave123"));

        assertThat(actualizado.getUsuario()).isEqualTo("fabian");
        assertThat(actualizado.getPasswordHash()).isEqualTo("hash");
        assertThat(EmpleadoService.tieneAcceso(actualizado)).isTrue();
    }

    @Test
    void actualizarTecnico_conAccesoYSinPasswordNueva_conservaLaContrasena() {
        Empleado existente = tecnicoSinAcceso();
        existente.setUsuario("fabian");
        existente.setPasswordHash("hash-viejo");

        Empleado actualizado = empleadoService.actualizarTecnico(5, tecnicoRequest("fabian2", null));

        assertThat(actualizado.getUsuario()).isEqualTo("fabian2");
        assertThat(actualizado.getPasswordHash()).isEqualTo("hash-viejo");
    }
}
