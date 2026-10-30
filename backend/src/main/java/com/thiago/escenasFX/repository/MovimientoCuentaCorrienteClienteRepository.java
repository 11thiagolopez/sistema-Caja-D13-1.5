package com.thiago.escenasFX.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;

public interface MovimientoCuentaCorrienteClienteRepository
        extends JpaRepository<MovimientoCuentaCorrienteCliente, Integer> {

    List<MovimientoCuentaCorrienteCliente> findByClienteIdClienteOrderByFechaDesc(Integer idCliente);
}
