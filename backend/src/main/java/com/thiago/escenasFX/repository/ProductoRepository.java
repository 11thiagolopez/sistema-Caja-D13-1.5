package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByActivoTrueOrderByDescripcionAsc();

    List<Producto> findByRubroAndFamiliaAndNumeroMarcaOrderByCorrelativoDesc(String rubro, String familia, String numeroMarca);

    boolean existsByNumeroMarca(String numeroMarca);

    // Los productos históricos (migrados antes del catálogo Marca, o backfileados a mano en
    // Supabase) tienen numeroMarca/marca en texto libre sin pasar por MarcaService — y un mismo
    // numeroMarca puede repetirse para nombres distintos según el rubro. Antes de generar un
    // código nuevo para un nombre que se tipea por primera vez en el catálogo, hay que revisar
    // si ese nombre ya se usaba en productos reales y, si es así, reciclar el (numeroMarca,
    // marca) combinado que más se repite — si no, se termina asignando un código nuevo a una
    // marca que en realidad ya tenía uno, partiendo en dos el mismo nombre con dos códigos
    // distintos (bug real detectado: "kallay" ya usaba "01"/"21", pero al tipearlo en minúscula
    // se le asignó "42" porque el catálogo Marca todavía no lo conocía).
    @Query("SELECT p.numeroMarca, p.marca, COUNT(p) as cnt FROM Producto p "
        + "WHERE UPPER(p.marca) = UPPER(:nombre) AND p.numeroMarca IS NOT NULL "
        + "GROUP BY p.numeroMarca, p.marca ORDER BY cnt DESC")
    List<Object[]> buscarUsoHistoricoDeMarca(@Param("nombre") String nombre);

    // Explícito con @Query (en vez de un nombre derivado tipo
    // findByCodigoFabricaOrCodigoInternoAndActivoTrue) porque Spring Data resolvería
    // "X Or Y And Z" como "X Or (Y And Z)", no "(X Or Y) And Z".
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND (p.codigoFabrica = :codigo OR p.codigoInterno = :codigo)")
    Optional<Producto> buscarActivoPorCodigo(@Param("codigo") String codigo);
}
