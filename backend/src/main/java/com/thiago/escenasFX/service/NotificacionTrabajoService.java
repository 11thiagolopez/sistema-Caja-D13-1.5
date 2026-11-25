package com.thiago.escenasFX.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Notificacion;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.NotificacionRepository;

/**
 * Avisos de trabajos a domicilio (campanita + push con contenido). Siempre deja la fila en
 * `notificaciones` primero, y el push es best-effort encima — si el push falla o el dispositivo no
 * tiene claves de cifrado todavía, el aviso de todos modos queda para verlo entrando al sistema.
 */
@Service
public class NotificacionTrabajoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionTrabajoService.class);
    private static final String ROL_ADMIN = "ADMIN";
    private static final int MAX_TEXTO = 255;

    private final NotificacionRepository notificacionRepo;
    private final EmpleadoRepository empleadoRepo;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    public NotificacionTrabajoService(NotificacionRepository notificacionRepo, EmpleadoRepository empleadoRepo,
            PushService pushService, ObjectMapper objectMapper) {
        this.notificacionRepo = notificacionRepo;
        this.empleadoRepo = empleadoRepo;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    /** Avisos dirigidos al técnico de un trabajo (nuevo, reprogramado, cancelado). */
    @Transactional
    public void avisarATecnico(Empleado tecnico, String tipo, String texto, Venta venta) {
        guardarYEnviar(tecnico, tipo, texto, venta, "/mis-trabajos/" + venta.getIdVenta());
    }

    /** Avisos dirigidos al jefe (en camino, llegó, terminado, nota) — a todos los ADMIN activos. */
    @Transactional
    public void avisarATodosLosAdmin(String tipo, String texto, Venta venta) {
        String url = "/ventas/domicilio?id=" + venta.getIdVenta();
        for (Empleado admin : empleadoRepo.findByRolAndActivoTrue(ROL_ADMIN)) {
            guardarYEnviar(admin, tipo, texto, venta, url);
        }
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listar(Empleado empleado) {
        return notificacionRepo.findTop50ByEmpleado_IdEmpleadoOrderByCreadaEnDesc(empleado.getIdEmpleado());
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(Empleado empleado) {
        return notificacionRepo.countByEmpleado_IdEmpleadoAndLeidaFalse(empleado.getIdEmpleado());
    }

    @Transactional
    public void marcarLeida(Integer idNotificacion, Empleado empleado) {
        Notificacion notificacion = notificacionRepo
            .findByIdNotificacionAndEmpleado_IdEmpleado(idNotificacion, empleado.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Notificación no existe: " + idNotificacion));
        notificacion.setLeida(true);
        notificacionRepo.save(notificacion);
    }

    private void guardarYEnviar(Empleado destinatario, String tipo, String texto, Venta venta, String url) {
        String textoAcotado = texto.length() > MAX_TEXTO ? texto.substring(0, MAX_TEXTO - 1) + "…" : texto;

        Notificacion notificacion = new Notificacion();
        notificacion.setEmpleado(destinatario);
        notificacion.setTipo(tipo);
        notificacion.setTexto(textoAcotado);
        notificacion.setVenta(venta);
        notificacionRepo.save(notificacion);

        try {
            String payload = objectMapper.writeValueAsString(Map.of("tipo", tipo, "cuerpo", textoAcotado, "url", url));
            pushService.avisarConContenido(destinatario, payload);
        } catch (Exception e) {
            log.error("No se pudo armar o mandar el push del aviso \"{}\" (#{}): {}", tipo,
                notificacion.getIdNotificacion(), e.getMessage(), e);
        }
    }
}
