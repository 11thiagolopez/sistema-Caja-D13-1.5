package com.thiago.escenasFX.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.SesionCaja;

public interface SesionCajaRepository extends JpaRepository<SesionCaja, Integer> {
    Optional<SesionCaja> findByFechaAndEstado(LocalDate fecha, String estado);
}
