package com.thiago.escenasFX.service;

import java.text.Normalizer;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * "Historial del cliente" (Etapa 3 del documento del dueño): al cargar un trabajo, los anteriores
 * del mismo teléfono o de la misma dirección, para saber qué se hizo antes (ej. qué cerradura se
 * puso). El teléfono se compara por sus últimos 8 dígitos ("11 5555-1234", "+54 9 11 5555 1234" y
 * "1155551234" son el mismo); la dirección, sin mayúsculas, tildes, puntuación ni espacios de más.
 */
@Service
public class HistorialClienteService {

    static final int MAX_RESULTADOS = 15;
    private static final int DIGITOS_TELEFONO = 8;

    private final VentaRepository ventaRepo;

    public HistorialClienteService(VentaRepository ventaRepo) {
        this.ventaRepo = ventaRepo;
    }

    @Transactional(readOnly = true)
    public List<Venta> buscar(String telefono, String direccion, Integer excluirIdVenta) {
        String tel = claveTelefono(telefono);
        String dir = claveDireccion(direccion);
        if (tel == null && dir == null) {
            return List.of();
        }
        return ventaRepo.findByTipoVentaAndEstadoNotOrderByFechaProgramadaDescIdVentaDesc("DOMICILIO", "ANULADA")
            .stream()
            .filter(v -> excluirIdVenta == null || !excluirIdVenta.equals(v.getIdVenta()))
            .filter(v -> (tel != null && tel.equals(claveTelefono(v.getClienteTelefono())))
                || (dir != null && dir.equals(claveDireccion(v.getDireccionTrabajo()))))
            .limit(MAX_RESULTADOS)
            .toList();
    }

    /** Últimos 8 dígitos; null si tiene menos (un número incompleto no identifica a nadie). */
    static String claveTelefono(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("[^0-9]", "");
        return digitos.length() < DIGITOS_TELEFONO ? null : digitos.substring(digitos.length() - DIGITOS_TELEFONO);
    }

    /** "Av. Rivadavia  5200, 3°B" → "av rivadavia 5200 3 b"; null si queda casi vacía. */
    static String claveDireccion(String direccion) {
        if (direccion == null) {
            return null;
        }
        // NFD separa la tilde de su letra ("á" → "a" + marca) y acá se borran las marcas, antes de
        // que el filtro de abajo las convierta en un espacio ("echeverri a").
        String sinTildes = Normalizer.normalize(direccion, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String clave = sinTildes.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        return clave.length() < 4 ? null : clave;
    }
}
