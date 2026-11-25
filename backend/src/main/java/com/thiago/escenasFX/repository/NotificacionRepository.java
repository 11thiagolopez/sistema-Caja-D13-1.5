package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    List<Notificacion> findTop50ByEmpleado_IdEmpleadoOrderByCreadaEnDesc(Integer idEmpleado);

    long countByEmpleado_IdEmpleadoAndLeidaFalse(Integer idEmpleado);

    Optional<Notificacion> findByIdNotificacionAndEmpleado_IdEmpleado(Integer idNotificacion, Integer idEmpleado);
}
