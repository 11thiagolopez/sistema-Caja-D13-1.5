package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.SuscripcionPush;

public interface SuscripcionPushRepository extends JpaRepository<SuscripcionPush, Integer> {

    Optional<SuscripcionPush> findByEndpoint(String endpoint);

    List<SuscripcionPush> findByEmpleado_RolAndEmpleado_ActivoTrue(String rol);
}
