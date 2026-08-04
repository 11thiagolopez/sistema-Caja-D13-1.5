package com.thiago.escenasFX.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.GastoRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.repository.GastoRepository;

@Service
public class GastoService {

    private final GastoRepository gastoRepo;

    public GastoService(GastoRepository gastoRepo) {
        this.gastoRepo = gastoRepo;
    }

    public Gasto crear(GastoRequest req, Empleado empleado) {
        if (req.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del gasto no puede ser futura");
        }

        Gasto gasto = new Gasto();
        gasto.setNombre(req.getNombre());
        gasto.setImporte(req.getImporte());
        gasto.setFecha(req.getFecha());
        gasto.setCategoria(req.getCategoria());
        gasto.setEmpleadoRegistro(empleado);
        return gastoRepo.save(gasto);
    }

    public List<Gasto> listarPorRango(LocalDate desde, LocalDate hasta) {
        return gastoRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }
}
