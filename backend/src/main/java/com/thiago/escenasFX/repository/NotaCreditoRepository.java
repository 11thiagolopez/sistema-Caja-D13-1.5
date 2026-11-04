package com.thiago.escenasFX.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.NotaCredito;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Integer> {

    List<NotaCredito> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);

    boolean existsByVentaOriginalIdVenta(Integer idVenta);

    @Query("select distinct n.ventaOriginal.idVenta from NotaCredito n where n.ventaOriginal.idVenta in :ids")
    List<Integer> idsDeVentasConNota(@Param("ids") Collection<Integer> ids);

    @Query("select coalesce(sum(n.montoTotal), 0) from NotaCredito n where n.ventaOriginal.idVenta = :idVenta")
    BigDecimal sumarMontoPorVenta(@Param("idVenta") Integer idVenta);
}
