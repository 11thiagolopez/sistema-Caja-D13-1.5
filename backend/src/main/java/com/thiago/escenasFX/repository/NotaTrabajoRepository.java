package com.thiago.escenasFX.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.NotaTrabajo;

public interface NotaTrabajoRepository extends JpaRepository<NotaTrabajo, Integer> {

    List<NotaTrabajo> findByVentaIdVentaOrderByCreadoEnAsc(Integer idVenta);
}
