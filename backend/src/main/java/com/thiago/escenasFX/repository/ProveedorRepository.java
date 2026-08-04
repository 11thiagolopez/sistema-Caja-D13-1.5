package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

    Optional<Proveedor> findByNombreIgnoreCase(String nombre);

    List<Proveedor> findByActivoTrueOrderByNombreAsc();
}
