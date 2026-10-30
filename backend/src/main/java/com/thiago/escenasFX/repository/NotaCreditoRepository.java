package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.NotaCredito;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Integer> {

    List<NotaCredito> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);
}
