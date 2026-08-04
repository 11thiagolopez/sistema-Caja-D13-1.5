package com.thiago.escenasFX.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.MarcaResponse;
import com.thiago.escenasFX.service.MarcaService;

@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    private final MarcaService marcaService;

    public MarcaController(MarcaService marcaService) {
        this.marcaService = marcaService;
    }

    @GetMapping
    public List<MarcaResponse> listar() {
        return marcaService.listarActivas().stream()
            .map(m -> new MarcaResponse(m.getIdMarca(), m.getNombre(), m.getCodigo()))
            .toList();
    }
}
