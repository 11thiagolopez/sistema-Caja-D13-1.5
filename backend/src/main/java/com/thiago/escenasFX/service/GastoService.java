package com.thiago.escenasFX.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.GastoRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.repository.GastoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;

@Service
public class GastoService {

    private static final List<String> MEDIOS_DE_PAGO = List.of("EFECTIVO", "TRANSFERENCIA");
    private static final String ORIGEN_CAJA = "CAJA";
    private static final List<String> ORIGENES = List.of(ORIGEN_CAJA, "EXTERNO");

    private final GastoRepository gastoRepo;
    private final SesionCajaRepository sesionRepo;

    public GastoService(GastoRepository gastoRepo, SesionCajaRepository sesionRepo) {
        this.gastoRepo = gastoRepo;
        this.sesionRepo = sesionRepo;
    }

    @Transactional
    public Gasto crear(GastoRequest req, Empleado empleado) {
        if (req.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del gasto no puede ser futura");
        }
        if (req.getMedioPago() == null || !MEDIOS_DE_PAGO.contains(req.getMedioPago())) {
            throw new IllegalArgumentException("El medio de pago de un gasto debe ser efectivo o transferencia");
        }
        if (req.getOrigen() == null || !ORIGENES.contains(req.getOrigen())) {
            throw new IllegalArgumentException("Hay que indicar el origen del gasto: caja o externo");
        }

        SesionCaja sesion = sesionRepo.findByEstado("ABIERTA").orElse(null);
        if (ORIGEN_CAJA.equals(req.getOrigen()) && sesion == null) {
            throw new IllegalStateException("No hay una caja abierta de la que pueda salir este gasto");
        }

        Gasto gasto = new Gasto();
        gasto.setNombre(req.getNombre());
        gasto.setImporte(req.getImporte());
        gasto.setFecha(req.getFecha());
        gasto.setCategoria(req.getCategoria());
        gasto.setMedioPago(req.getMedioPago());
        gasto.setOrigen(req.getOrigen());
        gasto.setSesion(sesion);
        gasto.setEmpleadoRegistro(empleado);
        return gastoRepo.save(gasto);
    }

    public List<Gasto> listarPorRango(LocalDate desde, LocalDate hasta) {
        return gastoRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }
}
