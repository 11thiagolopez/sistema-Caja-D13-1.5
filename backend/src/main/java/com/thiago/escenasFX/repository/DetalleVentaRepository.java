package com.thiago.escenasFX.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.DetalleVenta;

// No existía (DetalleVenta solo se accedía vía Venta.detalles, cascade). Nota de Crédito necesita
// ubicar una línea de venta puntual por id para validarla/acreditarla sin cargar la venta entera.
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Integer> {
}
