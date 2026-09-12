package com.thiago.escenasFX.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thiago.escenasFX.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByActivoTrueOrderByDescripcionAsc();

    // Usado por ProductoService para validar unicidad antes de asignar codigo_barras (alta y
    // edición) — no se usa para el escaneo en Ventas, que sigue con buscarActivoPorCodigo.
    Optional<Producto> findByCodigoBarras(String codigoBarras);

    // Usado en el camino de "verificar stock y descontar" (VentaService) en vez de findById:
    // PESSIMISTIC_WRITE hace un SELECT ... FOR UPDATE, así que si dos ventas del mismo producto
    // llegan al mismo tiempo, la segunda espera a que la primera confirme su transacción antes de
    // leer el stock — sin esto, las dos podían leer el mismo stockActual, pasar la validación
    // juntas y dejarlo en negativo (dos ventas de la última unidad, ambas "exitosas").
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.idProducto = :id")
    Optional<Producto> buscarPorIdConLock(@Param("id") Integer id);

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

    // Usado por el escaneo de Cobros y por "Cargar stock": busca por codigoBarras (el valor
    // vigente, ya sea el código de fábrica real o el interno de fallback — ver
    // ProductoService.asignarCodigoBarras) con codigoInterno como red de contención por si algún
    // producto quedó sin codigoBarras (alta hecha fuera de la app, directo en Supabase). Ya NO
    // busca por codigoFabrica crudo: antes de que existiera codigoBarras esa era la única forma de
    // resolver el código de fábrica, pero varios productos migrados históricos comparten el mismo
    // codigoFabrica entre sí (dato heredado, ver backfill de codigo_barras) — buscar por
    // codigoFabrica ahí era ambiguo y `Optional<Producto>` podía tirar NonUniqueResultException.
    // Explícito con @Query (en vez de un nombre derivado) porque Spring Data resolvería
    // "X Or Y And Z" como "X Or (Y And Z)", no "(X Or Y) And Z".
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND (p.codigoBarras = :codigo OR p.codigoInterno = :codigo)")
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
