package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.dto.ArchivoTrabajoResponse;
import com.thiago.escenasFX.model.ArchivoTrabajo;

public interface ArchivoTrabajoRepository extends JpaRepository<ArchivoTrabajo, Integer> {

    @Query("select new com.thiago.escenasFX.dto.ArchivoTrabajoResponse(a.idArchivo, e.idEmpleado, e.nombre, a.creadaEn, "
        + "a.momento, a.tamanioBytes, a.tipoContenido) "
        + "from ArchivoTrabajo a join a.empleado e where a.venta.idVenta = :idVenta order by a.creadaEn asc, a.idArchivo asc")
    List<ArchivoTrabajoResponse> listarDelTrabajo(@Param("idVenta") Integer idVenta);

    /** Cuántos videos (o audios) tiene un trabajo: {@code prefijo} = "video/" o "audio/". */
    long countByVenta_IdVentaAndTipoContenidoStartingWith(Integer idVenta, String prefijo);

    Optional<ArchivoTrabajo> findByIdArchivoAndVenta_IdVenta(Integer idArchivo, Integer idVenta);
}
