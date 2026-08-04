package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Marca;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {

    Optional<Marca> findByNombreIgnoreCase(String nombre);

    List<Marca> findByActivoTrueOrderByNombreAsc();

    boolean existsByCodigo(String codigo);
}
