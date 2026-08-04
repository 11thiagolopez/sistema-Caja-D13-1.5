package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByActivoTrueOrderByDescripcionAsc();

    List<Producto> findByRubroAndFamiliaAndMarcaOrderByCorrelativoDesc(String rubro, String familia, String marca);

    boolean existsByMarca(String marca);

    // Explícito con @Query (en vez de un nombre derivado tipo
    // findByCodigoFabricaOrCodigoInternoAndActivoTrue) porque Spring Data resolvería
    // "X Or Y And Z" como "X Or (Y And Z)", no "(X Or Y) And Z".
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND (p.codigoFabrica = :codigo OR p.codigoInterno = :codigo)")
    Optional<Producto> buscarActivoPorCodigo(@Param("codigo") String codigo);
}
