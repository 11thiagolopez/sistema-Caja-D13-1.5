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

    /**
     * Reproduce el bug real reportado el 2026-09-23: al cargar una marca nueva ("PROVENZA"), el
     * código histórico más usado por ese nombre en productos migrados ("01") ya estaba asignado
     * en el catálogo a OTRA marca (CAMBRE) — insertar una segunda fila con codigo="01" viola el
     * UNIQUE de marcas.codigo, y esa excepción de base no mapeada se colaba como un 500 genérico
     * ("Ocurrió un error inesperado") en vez de dar de alta el producto. El fix: si el código
     * histórico ya está tomado en el catálogo, se cae a un código nuevo en vez de reintentarlo.
     */
    @Test
    void resolverOCrear_codigoHistoricoYaTomadoPorOtraMarca_caeAUnCodigoNuevo() {
        when(marcaRepo.findByNombreIgnoreCase("PROVENZA")).thenReturn(Optional.empty());
        when(productoRepo.buscarUsoHistoricoDeMarca("PROVENZA")).thenReturn(
            List.<Object[]>of(new Object[] { "01", "PROVENZA", 2L }));
        when(marcaRepo.existsByCodigo("01")).thenReturn(true);
        when(marcaRepo.existsByCodigo("100")).thenReturn(false);
        when(productoRepo.existsByNumeroMarca("100")).thenReturn(false);
        when(marcaRepo.save(any(Marca.class))).thenAnswer(inv -> inv.getArgument(0));

        Marca resultado = marcaService.resolverOCrear("PROVENZA");

        assertThat(resultado.getCodigo()).isEqualTo("100");
        assertThat(resultado.getNombre()).isEqualTo("PROVENZA");
    }

    /**
     * El rango de 2 dígitos ("00" a "99") se agotó en la base real (confirmado por consulta
     * directa a Supabase el 2026-09-23) — el generador de códigos nuevos ahora arranca en "100"
     * (3 dígitos, ver siguienteCodigoLibre).
     */
    @Test
    void resolverOCrear_nombreNuncaUsado_generaCodigoNuevoDesde100() {
        when(marcaRepo.findByNombreIgnoreCase("MARCA NUEVA")).thenReturn(Optional.empty());
        when(productoRepo.buscarUsoHistoricoDeMarca("MARCA NUEVA")).thenReturn(List.of());
        when(marcaRepo.existsByCodigo("100")).thenReturn(false);
        when(productoRepo.existsByNumeroMarca("100")).thenReturn(false);
        when(marcaRepo.save(any(Marca.class))).thenAnswer(inv -> inv.getArgument(0));

        Marca resultado = marcaService.resolverOCrear("MARCA NUEVA");

        assertThat(resultado.getCodigo()).isEqualTo("100");
        assertThat(resultado.getNombre()).isEqualTo("MARCA NUEVA");
    }
}
