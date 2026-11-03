package com.thiago.escenasFX.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.SolicitudRetiro;

import jakarta.persistence.LockModeType;

public interface SolicitudRetiroRepository extends JpaRepository<SolicitudRetiro, Integer> {

    List<SolicitudRetiro> findByEstadoOrderByCreadoEnAsc(String estado);

    List<SolicitudRetiro> findByIdSolicitudInAndEmpleadoSolicitante_IdEmpleado(Collection<Integer> ids,
            Integer idEmpleado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SolicitudRetiro s WHERE s.idSolicitud = :id")
    Optional<SolicitudRetiro> buscarPorIdConLock(@Param("id") Integer id);
}
