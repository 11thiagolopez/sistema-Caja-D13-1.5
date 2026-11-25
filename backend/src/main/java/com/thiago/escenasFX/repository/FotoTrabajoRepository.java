package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.dto.FotoTrabajoResponse;
import com.thiago.escenasFX.model.FotoTrabajo;

public interface FotoTrabajoRepository extends JpaRepository<FotoTrabajo, Integer> {

    // Proyección: listar las fotos de un trabajo no tiene que bajar los bytes de todas.
    @Query("select new com.thiago.escenasFX.dto.FotoTrabajoResponse(f.idFoto, e.idEmpleado, e.nombre, f.creadaEn) "
        + "from FotoTrabajo f join f.empleado e where f.venta.idVenta = :idVenta order by f.creadaEn asc, f.idFoto asc")
    List<FotoTrabajoResponse> listarDelTrabajo(@Param("idVenta") Integer idVenta);

    long countByVenta_IdVenta(Integer idVenta);

    Optional<FotoTrabajo> findByIdFotoAndVenta_IdVenta(Integer idFoto, Integer idVenta);
}
