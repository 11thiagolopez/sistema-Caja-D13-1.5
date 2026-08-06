package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.model.Marca;
import com.thiago.escenasFX.repository.MarcaRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class MarcaServiceTest {

    @Mock
    private MarcaRepository marcaRepo;

    @Mock
    private ProductoRepository productoRepo;

    @InjectMocks
    private MarcaService marcaService;

    @Test
    void resolverOCrear_marcaYaEnElCatalogo_laDevuelveSinTocarProductos() {
        Marca existente = new Marca();
        existente.setNombre("KALOP");
        existente.setCodigo("12");
        when(marcaRepo.findByNombreIgnoreCase("kalop")).thenReturn(Optional.of(existente));

        Marca resultado = marcaService.resolverOCrear("kalop");

        assertThat(resultado).isSameAs(existente);
        verify(productoRepo, never()).buscarUsoHistoricoDeMarca(anyString());
    }

    /**
     * Reproduce el bug real: "kallay" (minúscula, no estaba en el catálogo Marca) con productos
     * históricos ya usando "KALLAY" bajo dos códigos distintos ("01" con 3 usos, "21" con 35).
     * Antes del fix esto generaba un código nuevo (41+) en vez de reciclar "21".
     */
    @Test
    void resolverOCrear_nombreSinCatalogoPeroUsadoEnProductos_reciclaElCodigoMasUsado() {
        when(marcaRepo.findByNombreIgnoreCase("kallay")).thenReturn(Optional.empty());
        when(productoRepo.buscarUsoHistoricoDeMarca("kallay")).thenReturn(
            List.of(new Object[] { "21", "KALLAY", 35L }, new Object[] { "01", "KALLAY", 3L }));
        when(marcaRepo.save(any(Marca.class))).thenAnswer(inv -> inv.getArgument(0));

        Marca resultado = marcaService.resolverOCrear("kallay");

        assertThat(resultado.getCodigo()).isEqualTo("21");
        // Capitalización histórica real, no la que tipeó quien cargó el producto nuevo.
        assertThat(resultado.getNombre()).isEqualTo("KALLAY");
    }

    @Test
    void resolverOCrear_nombreNuncaUsado_generaCodigoNuevoDesde41() {
        when(marcaRepo.findByNombreIgnoreCase("MARCA NUEVA")).thenReturn(Optional.empty());
        when(productoRepo.buscarUsoHistoricoDeMarca("MARCA NUEVA")).thenReturn(List.of());
        when(marcaRepo.existsByCodigo("41")).thenReturn(false);
        when(productoRepo.existsByNumeroMarca("41")).thenReturn(false);
        when(marcaRepo.save(any(Marca.class))).thenAnswer(inv -> inv.getArgument(0));

        Marca resultado = marcaService.resolverOCrear("MARCA NUEVA");

        assertThat(resultado.getCodigo()).isEqualTo("41");
        assertThat(resultado.getNombre()).isEqualTo("MARCA NUEVA");
    }
}
