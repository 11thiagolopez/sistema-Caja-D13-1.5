package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepo;

    @InjectMocks
    private ProductoService productoService;

    private ProductoRequest request(String rubro, String familia, String marca) {
        ProductoRequest req = new ProductoRequest();
        req.setRubro(rubro);
        req.setFamilia(familia);
        req.setMarca(marca);
        req.setProveedor("Proveedor SA");
        req.setDescripcion("Destornillador");
        req.setPrecioVenta(new BigDecimal("100"));
        req.setStockActual(10);
        return req;
    }

    private Producto productoConCorrelativo(String correlativo) {
        Producto p = new Producto();
        p.setCorrelativo(correlativo);
        return p;
    }

    @Test
    void crear_primeraCombinacionRubroFamiliaMarca_correlativoArrancaEn0001() {
        when(productoRepo.findByRubroAndFamiliaAndMarcaOrderByCorrelativoDesc("01", "05", "02"))
            .thenReturn(List.of());
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto creado = productoService.crear(request("01", "05", "02"));

        assertThat(creado.getCorrelativo()).isEqualTo("0001");
        assertThat(creado.getCodigoInterno()).isEqualTo("0105020001");
        assertThat(creado.isActivo()).isTrue();
    }

    @Test
    void crear_combinacionYaExistente_siguienteCorrelativo() {
        when(productoRepo.findByRubroAndFamiliaAndMarcaOrderByCorrelativoDesc("01", "05", "02"))
            .thenReturn(List.of(productoConCorrelativo("0002"), productoConCorrelativo("0001")));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto creado = productoService.crear(request("01", "05", "02"));

        assertThat(creado.getCorrelativo()).isEqualTo("0003");
        assertThat(creado.getCodigoInterno()).isEqualTo("0105020003");
    }

    @Test
    void crear_combinacionDistinta_reiniciaCorrelativoEn0001() {
        when(productoRepo.findByRubroAndFamiliaAndMarcaOrderByCorrelativoDesc("02", "01", "09"))
            .thenReturn(List.of());
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto creado = productoService.crear(request("02", "01", "09"));

        assertThat(creado.getCorrelativo()).isEqualTo("0001");
        assertThat(creado.getCodigoInterno()).isEqualTo("0201090001");
    }

    @Test
    void eliminar_marcaInactivoSinBorrarLaFila() {
        Producto producto = new Producto();
        producto.setIdProducto(5);
        producto.setActivo(true);
        when(productoRepo.findById(5)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        productoService.eliminar(5);

        assertThat(producto.isActivo()).isFalse();
    }

    @Test
    void cargarStock_codigoExistente_sumaCantidad() {
        Producto producto = new Producto();
        producto.setStockActual(10);
        when(productoRepo.buscarActivoPorCodigo("779123")).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto actualizado = productoService.cargarStock("779123", 5);

        assertThat(actualizado.getStockActual()).isEqualTo(15);
    }

    @Test
    void cargarStock_codigoInexistente_lanzaExcepcion() {
        when(productoRepo.buscarActivoPorCodigo("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.cargarStock("000000", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no encontrado");
    }
}
