package com.thiago.escenasFX.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.HistorialEstadoTrabajo;

public interface HistorialEstadoTrabajoRepository extends JpaRepository<HistorialEstadoTrabajo, Integer> {

    List<HistorialEstadoTrabajo> findByVentaIdVentaOrderByCreadoEnAsc(Integer idVenta);
}
