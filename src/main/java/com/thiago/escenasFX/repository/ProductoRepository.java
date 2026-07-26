package com.thiago.escenasFX.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thiago.escenasFX.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
}
