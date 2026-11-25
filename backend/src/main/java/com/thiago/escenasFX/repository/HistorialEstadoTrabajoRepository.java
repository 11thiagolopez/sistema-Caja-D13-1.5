package com.thiago.escenasFX.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.HistorialEstadoTrabajo;

public interface HistorialEstadoTrabajoRepository extends JpaRepository<HistorialEstadoTrabajo, Integer> {

    List<HistorialEstadoTrabajo> findByVentaIdVentaOrderByCreadoEnAsc(Integer idVenta);

    // Para los tiempos del reporte de trabajos: el historial de muchos trabajos de una sola vez.
    List<HistorialEstadoTrabajo> findByVentaIdVentaInOrderByCreadoEnAsc(java.util.Collection<Integer> idsVenta);
}
