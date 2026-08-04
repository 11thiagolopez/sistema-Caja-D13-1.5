package com.thiago.escenasFX.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Compra;

public interface CompraRepository extends JpaRepository<Compra, Integer> {

    List<Compra> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);
}
