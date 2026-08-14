package com.thiago.escenasFX.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Dolarización: recálculo masivo al abrir caja. Solo toca productos que ya tienen ancla en
    // USD (precio_venta_usd IS NOT NULL) — un producto dado de alta antes de que exista alguna
    // cotización queda afuera hasta que se le vuelva a fijar un precio (ProductoService.
    // sincronizarAnclaUsd). Nativo porque el redondeo a múltiplos de $100 (ROUND(x/100)*100) es
    // más simple en SQL directo que en JPQL, y este proyecto ya apunta a Postgres sin necesidad
    // de portabilidad de dialecto.
    @Modifying
    @Query(value = "UPDATE productos SET "
        + "precio_venta = ROUND((precio_venta_usd * :cotizacion) / 100) * 100, "
        + "precio_compra = CASE WHEN precio_compra_usd IS NOT NULL "
        + "THEN ROUND((precio_compra_usd * :cotizacion) / 100) * 100 ELSE precio_compra END "
        + "WHERE activo = true AND precio_venta_usd IS NOT NULL",
        nativeQuery = true)
    int reajustarPreciosPorCotizacion(@Param("cotizacion") BigDecimal cotizacion);
}
