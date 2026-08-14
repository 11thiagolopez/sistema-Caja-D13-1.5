package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.CompraItemRequest;
import com.thiago.escenasFX.dto.CompraRequest;
import com.thiago.escenasFX.model.Compra;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.repository.CompraRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock
    private CompraRepository compraRepo;
    @Mock
    private ProductoRepository productoRepo;
    @Mock
    private ProductoService productoService;
    @Mock
    private ProveedorService proveedorService;

    @InjectMocks
    private CompraService compraService;

    @Test
    void registrarCompra_productoExistente_sincronizaAnclaUsdAntesDeGuardar() {
        Producto producto = new Producto();
        producto.setIdProducto(3);
        when(productoRepo.findById(3)).thenReturn(Optional.of(producto));
        when(productoRepo.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(proveedorService.resolverOCrear("Proveedor SA")).thenReturn(new Proveedor());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        CompraItemRequest item = new CompraItemRequest();
        item.setIdProducto(3);
        item.setCantidad(5);
        item.setPrecioCompraUnitario(new BigDecimal("100"));

        CompraRequest req = new CompraRequest();
        req.setIdEmpleado(1);
        req.setFecha(LocalDate.now());
        req.setProveedorNombre("Proveedor SA");
        req.setMedioPago("EFECTIVO");
        req.setItems(List.of(item));

        compraService.registrarCompra(req, new Empleado());

        verify(productoService).sincronizarAnclaUsd(producto);
        assertThat(producto.getStockActual()).isEqualTo(5);
        assertThat(producto.getPrecioCompra()).isEqualByComparingTo("100");
    }
}
