package com.thiago.escenasFX.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.SesionCaja;

public interface SesionCajaRepository extends JpaRepository<SesionCaja, Integer> {
    Optional<SesionCaja> findByFechaAndEstado(LocalDate fecha, String estado);

    // Sin filtro de fecha a propósito: una sesión abierta un día y nunca cerrada (el vendedor
    // cerró el navegador sin apretar "Cerrar caja") sigue "ABIERTA" al día siguiente. Buscar solo
    // por hoy dejaba abrir una sesión nueva encima de la vieja, acumulando sesiones ABIERTA sin
    // cerrar nunca en la base.
    Optional<SesionCaja> findByEstado(String estado);

    List<SesionCaja> findByFechaBetweenOrderByFechaAscIdSesionAsc(LocalDate desde, LocalDate hasta);
}
