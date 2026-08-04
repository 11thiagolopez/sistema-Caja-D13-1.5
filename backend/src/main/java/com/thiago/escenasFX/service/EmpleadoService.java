package com.thiago.escenasFX.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.EmpleadoRequest;
import com.thiago.escenasFX.dto.EmpleadoUpdateRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepo;
    private final PasswordEncoder passwordEncoder;

    public EmpleadoService(EmpleadoRepository empleadoRepo, PasswordEncoder passwordEncoder) {
        this.empleadoRepo = empleadoRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Empleado> listar() {
        return empleadoRepo.findByActivoTrueOrderByNombreAsc();
    }

    public Empleado crear(EmpleadoRequest req) {
        if (empleadoRepo.findByUsuario(req.getUsuario()).isPresent()) {
            throw new IllegalArgumentException("El usuario ya existe: " + req.getUsuario());
        }

        Empleado empleado = new Empleado();
        empleado.setNombre(req.getNombre());
        empleado.setUsuario(req.getUsuario());
        empleado.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        empleado.setEmail(req.getEmail());
        empleado.setRol(req.getRol());
        empleado.setComision(req.getComision());
        empleado.setActivo(true);
        return empleadoRepo.save(empleado);
    }

    public Empleado actualizar(Integer id, EmpleadoUpdateRequest req) {
        Empleado empleado = obtenerPorId(id);

        empleadoRepo.findByUsuario(req.getUsuario())
            .filter(otro -> !otro.getIdEmpleado().equals(id))
            .ifPresent(otro -> {
                throw new IllegalArgumentException("El usuario ya existe: " + req.getUsuario());
            });

        empleado.setNombre(req.getNombre());
        empleado.setUsuario(req.getUsuario());
        empleado.setEmail(req.getEmail());
        empleado.setRol(req.getRol());
        empleado.setComision(req.getComision());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            empleado.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        return empleadoRepo.save(empleado);
    }

    public void desactivar(Integer id) {
        Empleado empleado = obtenerPorId(id);
        empleado.setActivo(false);
        empleadoRepo.save(empleado);
    }

    private Empleado obtenerPorId(Integer id) {
        return empleadoRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + id));
    }
}
