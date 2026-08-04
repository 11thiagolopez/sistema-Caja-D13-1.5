package com.thiago.escenasFX.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Gasto;

public interface GastoRepository extends JpaRepository<Gasto, Integer> {

    List<Gasto> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);
}
