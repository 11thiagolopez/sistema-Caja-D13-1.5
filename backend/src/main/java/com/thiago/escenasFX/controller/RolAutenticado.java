package com.thiago.escenasFX.controller;

import org.springframework.security.core.Authentication;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;

/**
 * Quién opera sale siempre del JWT validado, nunca del idEmpleado que manda el frontend: ese id
 * viene en el body, así que un VENDEDOR podría mandar el de otra persona y figurar con otro nombre
 * en las peticiones que ve el ADMIN.
 */
final class RolAutenticado {

    private RolAutenticado() {
    }

    static boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    static Empleado empleado(Authentication authentication, EmpleadoRepository empleadoRepo) {
        return empleadoRepo.findByUsuario(authentication.getName())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + authentication.getName()));
    }

    /** Un ADMIN puede operar a nombre de otro empleado; un VENDEDOR solo a nombre propio. */
    static Empleado empleadoQueOpera(Authentication authentication, Integer idEmpleadoPedido,
            EmpleadoRepository empleadoRepo) {
        if (!esAdmin(authentication)) {
            return empleado(authentication, empleadoRepo);
        }
        return empleadoRepo.findById(idEmpleadoPedido)
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + idEmpleadoPedido));
    }
}
