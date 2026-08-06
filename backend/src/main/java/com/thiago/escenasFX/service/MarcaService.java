package com.thiago.escenasFX.service;

import java.util.List;
import java.util.Optional;

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
     * Busca una marca por nombre (sin importar mayúsculas/espacios) y la crea si no existe.
     * Si el nombre ya aparecía en productos reales (migración histórica o carga manual en
     * Supabase, ninguna de las dos pasó por este catálogo), reutiliza el numeroMarca que más se
     * repite para ese nombre — y también su capitalización real — en vez de inventar un código
     * nuevo. Solo un nombre que nunca se usó en ningún producto recibe un código nuevo, tomado a
     * partir de "41" porque los códigos "01" a "40" ya estaban en uso antes de este catálogo
     * (auditado contra los 7004 productos reales).
     */
    @Transactional
    public Marca resolverOCrear(String nombreTipeado) {
        String nombre = nombreTipeado.trim();
        return marcaRepo.findByNombreIgnoreCase(nombre)
            .or(() -> crearDesdeUsoHistorico(nombre))
            .orElseGet(() -> {
                Marca marca = new Marca();
                marca.setNombre(nombre);
                marca.setCodigo(siguienteCodigoLibre());
                marca.setActivo(true);
                return marcaRepo.save(marca);
            });
    }

    private Optional<Marca> crearDesdeUsoHistorico(String nombre) {
        List<Object[]> filas = productoRepo.buscarUsoHistoricoDeMarca(nombre);
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] masUsado = filas.get(0);
        Marca marca = new Marca();
        marca.setNombre((String) masUsado[1]);
        marca.setCodigo((String) masUsado[0]);
        marca.setActivo(true);
        return Optional.of(marcaRepo.save(marca));
    }

    private String siguienteCodigoLibre() {
        for (int i = 41; i <= 99; i++) {
            String codigo = String.format("%02d", i);
            if (!marcaRepo.existsByCodigo(codigo) && !productoRepo.existsByNumeroMarca(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No hay códigos de marca disponibles");
    }
}
