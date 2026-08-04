package com.thiago.escenasFX.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.ProveedorRequest;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.repository.ProveedorRepository;

@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepo;

    public ProveedorService(ProveedorRepository proveedorRepo) {
        this.proveedorRepo = proveedorRepo;
    }

    public List<Proveedor> listar() {
        return proveedorRepo.findByActivoTrueOrderByNombreAsc();
    }

    public Proveedor crear(ProveedorRequest req) {
        Proveedor proveedor = new Proveedor();
        aplicar(proveedor, req);
        return proveedorRepo.save(proveedor);
    }

    public Proveedor actualizar(Integer id, ProveedorRequest req) {
        Proveedor proveedor = obtenerPorId(id);
        aplicar(proveedor, req);
        return proveedorRepo.save(proveedor);
    }

    public void eliminar(Integer id) {
        Proveedor proveedor = obtenerPorId(id);
        proveedor.setActivo(false);
        proveedorRepo.save(proveedor);
    }

    private Proveedor obtenerPorId(Integer id) {
        return proveedorRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Proveedor no existe: " + id));
    }

    private void aplicar(Proveedor proveedor, ProveedorRequest req) {
        proveedor.setNombre(req.getNombre());
        proveedor.setContacto(req.getContacto());
        proveedor.setTelefono(req.getTelefono());
        proveedor.setEmail(req.getEmail());
    }

    /**
     * Busca un proveedor por nombre (sin importar mayúsculas/espacios) y lo crea si no existe.
     * Usado desde ProductoService y CompraService cuando el nombre viene tipeado libremente
     * (combo con autocompletado) en vez de elegido de una lista ya existente.
     */
    @Transactional
    public Proveedor resolverOCrear(String nombreTipeado) {
        String nombre = nombreTipeado.trim();
        return proveedorRepo.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Proveedor proveedor = new Proveedor();
            proveedor.setNombre(nombre);
            proveedor.setActivo(true);
            return proveedorRepo.save(proveedor);
        });
    }
}
