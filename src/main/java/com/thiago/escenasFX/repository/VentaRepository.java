package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Integer> {
    List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findByFechaBetweenAndEstado(LocalDateTime desde, LocalDateTime hasta, String estado);

    List<Venta> findBySesion_IdSesionAndEstado(Integer idSesion, String estado);
}
