package com.thiago.escenasFX.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.thiago.escenasFX.model.Producto;

@DataJpaTest
class ProductoRepositoryTest {

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private TestEntityManager em;

    private Producto producto(String codigoInterno, String codigoFabrica, String codigoBarras) {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(10);
        p.setPrecioVenta(new BigDecimal("100"));
        p.setCodigoInterno(codigoInterno);
        p.setCodigoFabrica(codigoFabrica);
        p.setCodigoBarras(codigoBarras);
        p.setActivo(true);
        return em.persistAndFlush(p);
    }

    @Test
    void buscarActivoPorCodigo_encuentraPorCodigoBarras() {
        producto("0101010001", "7791234567890", "7791234567890");

        Producto encontrado = productoRepo.buscarActivoPorCodigo("7791234567890").orElseThrow();

        assertThat(encontrado.getCodigoInterno()).isEqualTo("0101010001");
    }

    @Test
    void buscarActivoPorCodigo_encuentraPorCodigoInternoComoRedDeContencion() {
        // Producto sin codigoBarras (alta hecha fuera de la app, ej. directo en Supabase): sigue
        // siendo encontrable por su codigoInterno.
        producto("0101010002", null, null);

        Producto encontrado = productoRepo.buscarActivoPorCodigo("0101010002").orElseThrow();

        assertThat(encontrado.getCodigoInterno()).isEqualTo("0101010002");
    }

    @Test
    void buscarActivoPorCodigo_noEncuentraPorUnCodigoDeFabricaQueNoCoincideConCodigoBarras() {
        // Caso real de datos migrados: dos productos comparten el mismo codigoFabrica histórico,
        // así que a éste se le asignó codigoInterno como codigoBarras en el backfill (ver
        // migración agrega_codigo_barras_a_productos). Escanear ese codigoFabrica compartido ya no
        // debe resolver este producto — evita el NonUniqueResultException que tiraba la búsqueda
        // vieja por codigoFabrica crudo cuando dos productos activos lo compartían.
        producto("0101010003", "COMPARTIDO123", "0101010003");

        assertThat(productoRepo.buscarActivoPorCodigo("COMPARTIDO123")).isEmpty();
    }

    @Test
    void buscarActivoPorCodigo_productoInactivo_noSeEncuentra() {
        Producto p = producto("0101010004", "7799998887776", "7799998887776");
        p.setActivo(false);
        em.persistAndFlush(p);

        assertThat(productoRepo.buscarActivoPorCodigo("7799998887776")).isEmpty();
    }
}
