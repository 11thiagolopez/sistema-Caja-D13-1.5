package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.thiago.escenasFX.dto.ProductoUpdateRequest;
import com.thiago.escenasFX.model.Marca;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepo;

    @Mock
    private MarcaService marcaService;

    @Mock
    private ProveedorService proveedorService;

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

    private Marca marcaConCodigo(String codigo) {
        Marca m = new Marca();
        m.setCodigo(codigo);
        m.setNombre("Marca " + codigo);
        return m;
    }

    @Test
    void crear_primeraCombinacionRubroFamiliaMarca_correlativoArrancaEn0001() {
        when(marcaService.resolverOCrear(anyString())).thenReturn(marcaConCodigo("02"));
        when(productoRepo.findByRubroAndFamiliaAndNumeroMarcaOrderByCorrelativoDesc("01", "05", "02"))
            .thenReturn(List.of());
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto creado = productoService.crear(request("01", "05", "02"));

        assertThat(creado.getCorrelativo()).isEqualTo("0001");
        assertThat(creado.getCodigoInterno()).isEqualTo("0105020001");
        assertThat(creado.getNumeroMarca()).isEqualTo("02");
        assertThat(creado.getMarca()).isEqualTo("Marca 02");
        assertThat(creado.isActivo()).isTrue();
    }

    @Test
    void crear_combinacionYaExistente_siguienteCorrelativo() {
        when(marcaService.resolverOCrear(anyString())).thenReturn(marcaConCodigo("02"));
        when(productoRepo.findByRubroAndFamiliaAndNumeroMarcaOrderByCorrelativoDesc("01", "05", "02"))
            .thenReturn(List.of(productoConCorrelativo("0002"), productoConCorrelativo("0001")));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto creado = productoService.crear(request("01", "05", "02"));

        assertThat(creado.getCorrelativo()).isEqualTo("0003");
        assertThat(creado.getCodigoInterno()).isEqualTo("0105020003");
    }

    @Test
    void crear_combinacionDistinta_reiniciaCorrelativoEn0001() {
        when(marcaService.resolverOCrear(anyString())).thenReturn(marcaConCodigo("09"));
        when(productoRepo.findByRubroAndFamiliaAndNumeroMarcaOrderByCorrelativoDesc("02", "01", "09"))
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

    @Test
    void actualizar_soloCambiaLosCamposNoNulos() {
        Producto producto = new Producto();
        producto.setIdProducto(7);
        producto.setDescripcion("Original");
        producto.setMarca("Marca vieja");
        producto.setPrecioVenta(new BigDecimal("100"));
        producto.setStockActual(10);
        producto.setRubro("01");
        producto.setNumeroMarca("02");
        producto.setCodigoInterno("0105020001");
        when(productoRepo.findById(7)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoUpdateRequest req = new ProductoUpdateRequest();
        req.setPrecioVenta(new BigDecimal("150"));
        req.setStockActual(20);
        Producto actualizado = productoService.actualizar(7, req);

        assertThat(actualizado.getPrecioVenta()).isEqualByComparingTo("150");
        assertThat(actualizado.getStockActual()).isEqualTo(20);
        // No vinieron en el request: quedan como estaban.
        assertThat(actualizado.getDescripcion()).isEqualTo("Original");
        assertThat(actualizado.getMarca()).isEqualTo("Marca vieja");
        // Identidad del producto, nunca tocada por esta edición.
        assertThat(actualizado.getRubro()).isEqualTo("01");
        assertThat(actualizado.getNumeroMarca()).isEqualTo("02");
        assertThat(actualizado.getCodigoInterno()).isEqualTo("0105020001");
    }

    @Test
    void actualizar_idInexistente_lanzaExcepcion() {
        when(productoRepo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizar(999, new ProductoUpdateRequest()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
