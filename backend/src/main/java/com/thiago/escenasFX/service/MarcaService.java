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
     * nuevo. Solo un nombre que nunca se usó en ningún producto recibe un código nuevo, tomado
     * del rango de 3 dígitos ("100" en adelante — ver siguienteCodigoLibre) porque el rango
     * original de 2 dígitos ("00" a "99") quedó agotado en septiembre 2026.
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
        String codigoHistorico = (String) masUsado[0];
        // Bug real (2026-09-23): un mismo numeroMarca puede repetirse para nombres distintos en
        // los datos migrados (ruido heredado del sistema viejo, ver comentario de
        // buscarUsoHistoricoDeMarca — ej. "01" es CAMBRE para la mayoría de los productos pero
        // también ACYTRA/PRIVE/KALLAY para unos pocos). Si ese código ya quedó asignado en el
        // catálogo a OTRA marca, intentar reciclarlo acá violaba el UNIQUE de marcas.codigo y la
        // excepción de la base (no mapeada por el GlobalExceptionHandler) se colaba como un 500
        // genérico en vez de dar de alta el producto. En ese caso se cae a un código nuevo vía
        // siguienteCodigoLibre() en el orElseGet de resolverOCrear, igual que si el nombre nunca
        // se hubiera usado antes.
        if (marcaRepo.existsByCodigo(codigoHistorico)) {
            return Optional.empty();
        }
        Marca marca = new Marca();
        marca.setNombre((String) masUsado[1]);
        marca.setCodigo(codigoHistorico);
        marca.setActivo(true);
        return Optional.of(marcaRepo.save(marca));
    }

    /**
     * Bug real (2026-09-23): el rango original de 2 dígitos ("41" a "99", los únicos libres tras
     * auditar los 7004 productos migrados) se agotó por completo con el crecimiento orgánico del
     * catálogo — confirmado contra la base real: de "00" a "99" sólo "00" seguía sin usarse, ni en
     * marcas.codigo ni en productos.numero_marca. Toda marca nueva sin historial en productos
     * empezó a fallar con "No hay códigos de marca disponibles". Ampliado a 3 dígitos: arranca en
     * "100" (más allá de cualquier código de 2 dígitos posible, así nunca colisiona con uno viejo)
     * y sube hasta "999", dando margen para 900 marcas nuevas más. marcas.codigo se amplió de
     * varchar(2) a varchar(3) en la base — los códigos de 2 dígitos existentes quedan intactos y
     * conviven sin problema, codigoInterno no depende de un ancho fijo para la marca.
     */
    private String siguienteCodigoLibre() {
        for (int i = 100; i <= 999; i++) {
            String codigo = String.valueOf(i);
            if (!marcaRepo.existsByCodigo(codigo) && !productoRepo.existsByNumeroMarca(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No hay códigos de marca disponibles");
    }
}
