package com.thiago.escenasFX.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.DetalleNotaCredito;

public interface DetalleNotaCreditoRepository extends JpaRepository<DetalleNotaCredito, Integer> {

    // Cuánto ya se acreditó de una línea de venta original, sumando todas las notas de crédito
    // existentes — usado para rechazar una nueva nota que pida acreditar más que el remanente
    // (ver NotaCreditoService.crear).
    @Query("SELECT COALESCE(SUM(d.cantidad), 0) FROM DetalleNotaCredito d "
        + "WHERE d.detalleVentaOriginal.idDetalle = :idDetalle")
    int sumarCantidadAcreditada(@Param("idDetalle") Integer idDetalle);
}
