package com.thiago.escenasFX.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.FacturaFiscal;

public interface FacturaFiscalRepository extends JpaRepository<FacturaFiscal, Integer> {

    Optional<FacturaFiscal> findByVentaIdVenta(Integer idVenta);
}
