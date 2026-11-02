package com.thiago.escenasFX.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.Compra;

public interface CompraRepository extends JpaRepository<Compra, Integer> {

    List<Compra> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);

    @Query("SELECT c FROM Compra c WHERE c.proveedor.idProveedor = :idProveedor "
        + "AND c.totalCompra > COALESCE(c.montoPagado, 0) ORDER BY c.fecha ASC, c.idCompra ASC")
    List<Compra> buscarConSaldoPendiente(@Param("idProveedor") Integer idProveedor);
}
