package com.thiago.escenasFX.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.thiago.escenasFX.model.Empleado;

@DataJpaTest
class EmpleadoRepositoryTest {

    @Autowired
    private EmpleadoRepository empleadoRepo;

    private Empleado empleado(String usuario, String rol) {
        Empleado e = new Empleado();
        e.setNombre("Nombre " + usuario);
        e.setUsuario(usuario);
        e.setPasswordHash("hash-irrelevante");
        e.setRol(rol);
        return e;
    }

    @Test
    void findByUsuario_existente_loEncuentra() {
        empleadoRepo.save(empleado("aleja", "ADMIN"));

        assertThat(empleadoRepo.findByUsuario("aleja")).isPresent()
            .get().extracting(Empleado::getRol).isEqualTo("ADMIN");
    }

    @Test
    void findByUsuario_inexistente_devuelveVacio() {
        assertThat(empleadoRepo.findByUsuario("no-existe")).isEmpty();
    }

    @Test
    void findByRol_devuelveSoloLosDeEseRol() {
        empleadoRepo.save(empleado("admin1", "ADMIN"));
        empleadoRepo.save(empleado("admin2", "ADMIN"));
        empleadoRepo.save(empleado("vendedor1", "VENDEDOR"));

        assertThat(empleadoRepo.findByRol("ADMIN"))
            .extracting(Empleado::getUsuario)
            .containsExactlyInAnyOrder("admin1", "admin2");
    }
}
