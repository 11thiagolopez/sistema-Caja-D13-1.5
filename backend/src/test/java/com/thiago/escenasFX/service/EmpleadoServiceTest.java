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
}
