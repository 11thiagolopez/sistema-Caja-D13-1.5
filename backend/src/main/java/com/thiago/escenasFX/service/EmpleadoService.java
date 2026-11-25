package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.EmpleadoRequest;
import com.thiago.escenasFX.dto.EmpleadoUpdateRequest;
import com.thiago.escenasFX.dto.TecnicoRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

@Service
public class EmpleadoService {

    static final String PREFIJO_USUARIO_SIN_ACCESO = "tecnico-";

    private final EmpleadoRepository empleadoRepo;
    private final PasswordEncoder passwordEncoder;

    public EmpleadoService(EmpleadoRepository empleadoRepo, PasswordEncoder passwordEncoder) {
        this.empleadoRepo = empleadoRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Empleado> listar() {
        return empleadoRepo.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional
    public Empleado crear(EmpleadoRequest req) {
        validarUsuarioLibre(req.getUsuario(), null);

        Empleado empleado = new Empleado();
        empleado.setNombre(req.getNombre());
        empleado.setUsuario(req.getUsuario());
        empleado.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        empleado.setEmail(req.getEmail());
        empleado.setRol(req.getRol());
        empleado.setComision(comisionSegunRol(req.getRol(), req.getComision()));
        empleado.setActivo(true);
        return empleadoRepo.save(empleado);
    }

    @Transactional
    public Empleado actualizar(Integer id, EmpleadoUpdateRequest req) {
        Empleado empleado = obtenerPorId(id);
        validarUsuarioLibre(req.getUsuario(), id);

        empleado.setNombre(req.getNombre());
        empleado.setUsuario(req.getUsuario());
        empleado.setEmail(req.getEmail());
        empleado.setRol(req.getRol());
        empleado.setComision(comisionSegunRol(req.getRol(), req.getComision()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            empleado.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        return empleadoRepo.save(empleado);
    }

    /**
     * Por default un técnico no entra al sistema: se le generan usuario y contraseña al azar, que
     * nadie conoce, solo porque la tabla los exige. Si el pedido trae usuario/password (ej.
     * Fabián, para "Mis trabajos" desde el celular), se usan esos en su lugar.
     */
    @Transactional
    public Empleado crearTecnico(TecnicoRequest req) {
        boolean conAcceso = tieneTexto(req.getUsuario());
        if (conAcceso) {
            validarUsuarioLibre(req.getUsuario(), null);
            if (!tieneTexto(req.getPassword())) {
                throw new IllegalArgumentException("Para que el técnico pueda entrar al sistema hace falta una contraseña");
            }
        }

        Empleado tecnico = new Empleado();
        tecnico.setNombre(req.getNombre());
        tecnico.setUsuario(conAcceso ? req.getUsuario() : PREFIJO_USUARIO_SIN_ACCESO + UUID.randomUUID());
        tecnico.setPasswordHash(passwordEncoder.encode(conAcceso ? req.getPassword() : UUID.randomUUID().toString()));
        tecnico.setRol("TECNICO");
        tecnico.setComision(req.getComision());
        tecnico.setActivo(true);
        return empleadoRepo.save(tecnico);
    }

    @Transactional
    public Empleado actualizarTecnico(Integer id, TecnicoRequest req) {
        Empleado tecnico = obtenerPorId(id);
        if (!"TECNICO".equals(tecnico.getRol())) {
            throw new IllegalArgumentException("El empleado #" + id + " no es un técnico");
        }
        tecnico.setNombre(req.getNombre());
        tecnico.setComision(req.getComision());
        if (tieneTexto(req.getUsuario())) {
            validarUsuarioLibre(req.getUsuario(), id);
            // Darle acceso a uno que no lo tenía sin contraseña lo dejaría con la contraseña al
            // azar del alta, que nadie conoce: igual sin poder entrar.
            if (!tieneAcceso(tecnico) && !tieneTexto(req.getPassword())) {
                throw new IllegalArgumentException("Para darle acceso al técnico hace falta una contraseña");
            }
            tecnico.setUsuario(req.getUsuario());
            if (tieneTexto(req.getPassword())) {
                tecnico.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            }
        }
        return empleadoRepo.save(tecnico);
    }

    /** Un técnico dado de alta sin acceso queda con un usuario "tecnico-&lt;uuid&gt;" que nadie conoce. */
    public static boolean tieneAcceso(Empleado empleado) {
        return !empleado.getUsuario().startsWith(PREFIJO_USUARIO_SIN_ACCESO);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private void validarUsuarioLibre(String usuario, Integer idAIgnorar) {
        if (usuario != null && usuario.startsWith(PREFIJO_USUARIO_SIN_ACCESO)) {
            throw new IllegalArgumentException("El usuario no puede empezar con \"" + PREFIJO_USUARIO_SIN_ACCESO
                + "\": está reservado para los técnicos sin acceso al sistema");
        }
        empleadoRepo.findByUsuario(usuario)
            .filter(otro -> idAIgnorar == null || !otro.getIdEmpleado().equals(idAIgnorar))
            .ifPresent(otro -> {
                throw new IllegalArgumentException("El usuario ya existe: " + usuario);
            });
    }

    /**
     * Solo TECNICO cobra comisión (por mano de obra en trabajos a domicilio) — VENDEDOR tiene
     * sueldo fijo y ADMIN no cobra comisión. Se ignora cualquier valor que venga en el request
     * para otros roles, así no queda un dato inconsistente aunque el frontend lo permitiera.
     */
    private BigDecimal comisionSegunRol(String rol, BigDecimal comision) {
        return "TECNICO".equals(rol) ? comision : null;
    }

    @Transactional
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
