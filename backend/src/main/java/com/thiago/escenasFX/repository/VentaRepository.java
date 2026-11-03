package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.Venta;

import jakarta.persistence.LockModeType;

public interface VentaRepository extends JpaRepository<Venta, Integer> {
    List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findByFechaBetweenAndEstado(LocalDateTime desde, LocalDateTime hasta, String estado);

    List<Venta> findBySesion_IdSesionAndEstado(Integer idSesion, String estado);

    List<Venta> findByEstadoOrderByFechaAsc(String estado);

    List<Venta> findByIdVentaInAndEmpleado_IdEmpleado(Collection<Integer> ids, Integer idEmpleado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.idVenta = :id")
    Optional<Venta> buscarPorIdConLock(@Param("id") Integer id);
}
