package com.thiago.escenasFX.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.Marca;
import com.thiago.escenasFX.repository.MarcaRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class MarcaService {

    private final MarcaRepository marcaRepo;
    private final ProductoRepository productoRepo;

    public MarcaService(MarcaRepository marcaRepo, ProductoRepository productoRepo) {
        this.marcaRepo = marcaRepo;
        this.productoRepo = productoRepo;
    }

    public List<Marca> listarActivas() {
        return marcaRepo.findByActivoTrueOrderByNombreAsc();
    }

    /**
     * Busca una marca por nombre (sin importar mayúsculas/espacios) y la crea si no existe,
     * asignándole el próximo código de 2 dígitos libre. Se busca a partir de "41" porque los
     * códigos "01" a "40" ya estaban en uso por productos existentes antes de este catálogo
     * (auditado contra los 7004 productos reales) — no se reutilizan para no colisionar con el
     * esquema de codigoInterno (rubro+familia+marca+correlativo) que ya tenían esos productos.
     */
    @Transactional
    public Marca resolverOCrear(String nombreTipeado) {
        String nombre = nombreTipeado.trim();
        return marcaRepo.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Marca marca = new Marca();
            marca.setNombre(nombre);
            marca.setCodigo(siguienteCodigoLibre());
            marca.setActivo(true);
            return marcaRepo.save(marca);
        });
    }

    private String siguienteCodigoLibre() {
        for (int i = 41; i <= 99; i++) {
            String codigo = String.format("%02d", i);
            if (!marcaRepo.existsByCodigo(codigo) && !productoRepo.existsByMarca(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No hay códigos de marca disponibles");
    }
}
