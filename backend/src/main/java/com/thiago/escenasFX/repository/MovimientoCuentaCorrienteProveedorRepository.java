package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;

public interface MovimientoCuentaCorrienteProveedorRepository
        extends JpaRepository<MovimientoCuentaCorrienteProveedor, Integer> {

    List<MovimientoCuentaCorrienteProveedor> findByProveedorIdProveedorOrderByFechaDesc(Integer idProveedor);

    List<MovimientoCuentaCorrienteProveedor> findBySesion_IdSesion(Integer idSesion);

    List<MovimientoCuentaCorrienteProveedor> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);
}
