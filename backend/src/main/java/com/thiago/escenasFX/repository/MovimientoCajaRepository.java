package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.MovimientoCaja;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {
    List<MovimientoCaja> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<MovimientoCaja> findBySesion_IdSesion(Integer idSesion);
}
