package com.thiago.escenasFX.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class ProductoService {

    private final ProductoRepository productoRepo;

    public ProductoService(ProductoRepository productoRepo) {
        this.productoRepo = productoRepo;
    }

    public List<Producto> listarTodos() {
        return productoRepo.findAll();
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + id));
    }
}
