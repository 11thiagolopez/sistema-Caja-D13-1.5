package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Presupuesto;

public interface PresupuestoRepository extends JpaRepository<Presupuesto, Integer> {

    List<Presupuesto> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);
}
