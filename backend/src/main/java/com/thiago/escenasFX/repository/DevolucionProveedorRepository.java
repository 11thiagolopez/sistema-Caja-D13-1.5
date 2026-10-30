package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.DevolucionProveedor;

public interface DevolucionProveedorRepository extends JpaRepository<DevolucionProveedor, Integer> {

    List<DevolucionProveedor> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);
}
