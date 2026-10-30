package com.thiago.escenasFX.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;

public interface MovimientoCuentaCorrienteProveedorRepository
        extends JpaRepository<MovimientoCuentaCorrienteProveedor, Integer> {

    List<MovimientoCuentaCorrienteProveedor> findByProveedorIdProveedorOrderByFechaDesc(Integer idProveedor);
}
