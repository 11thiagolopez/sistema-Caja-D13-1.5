package com.thiago.escenasFX.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.CotizacionDolar;

public interface CotizacionDolarRepository extends JpaRepository<CotizacionDolar, Integer> {

    Optional<CotizacionDolar> findFirstByFechaOrderByCreadoEnDesc(LocalDate fecha);

    Optional<CotizacionDolar> findFirstByOrderByCreadoEnDesc();
}
