package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Empleado;

public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {
    Optional<Empleado> findByUsuario(String usuario);

    List<Empleado> findByRol(String rol);

    List<Empleado> findByActivoTrueOrderByNombreAsc();
}
