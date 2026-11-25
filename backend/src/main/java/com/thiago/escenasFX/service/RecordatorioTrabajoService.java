package com.thiago.escenasFX.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Recordatorio push al técnico 1 hora antes de un trabajo (Etapa 2 del documento del dueño). Cada
 * 5 minutos busca trabajos agendados cuya hora de inicio cae dentro de la próxima hora y le avisa
 * al técnico asignado, una sola vez por trabajo ({@code recordatorio_enviado}; VentaService lo
 * vuelve a false si el trabajo se reprograma o se reasigna).
 *
 * <p>Un trabajo cargado o reprogramado con menos de una hora de anticipación también recibe el
 * aviso en la siguiente pasada. Uno cuya hora ya pasó no: avisar tarde confunde más de lo que ayuda.
 *
 * <p>La pasada automática se apaga con {@code recordatorios.habilitados=false} (ver SchedulingConfig).
 * Los tests de integración la apagan y llaman a {@link #enviarPendientes(LocalDateTime)} a mano.
 */
@Service
public class RecordatorioTrabajoService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioTrabajoService.class);
    static final Duration ANTICIPACION = Duration.ofHours(1);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final VentaRepository ventaRepo;
    private final NotificacionTrabajoService notificacionTrabajoService;

    public RecordatorioTrabajoService(VentaRepository ventaRepo, NotificacionTrabajoService notificacionTrabajoService) {
        this.ventaRepo = ventaRepo;
        this.notificacionTrabajoService = notificacionTrabajoService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void enviarPendientes() {
        try {
            int enviados = enviarPendientes(ZonaNegocio.ahora());
            if (enviados > 0) {
                log.info("Recordatorios de trabajos enviados: {}", enviados);
            }
        } catch (Exception e) {
            // Que una pasada fallida (ej. la base no responde) no mate al scheduler: la próxima reintenta.
            log.error("Falló la pasada de recordatorios de trabajos: {}", e.getMessage(), e);
        }
    }

    /** Separado del {@code @Scheduled} para poder probarlo con un "ahora" fijo. */
    @Transactional
    public int enviarPendientes(LocalDateTime ahora) {
        LocalDateTime limite = ahora.plus(ANTICIPACION);
        List<Venta> candidatos = ventaRepo.buscarParaRecordatorio(ahora.toLocalDate(), limite.toLocalDate());
        int enviados = 0;
        for (Venta venta : candidatos) {
            LocalDateTime inicio = LocalDateTime.of(venta.getFechaProgramada(), venta.getHoraDesde());
            if (inicio.isBefore(ahora) || inicio.isAfter(limite)) {
                continue;
            }
            String donde = venta.getDireccionTrabajo() != null ? venta.getDireccionTrabajo() : "trabajo #" + venta.getIdVenta();
            notificacionTrabajoService.avisarATecnico(venta.getEmpleadoTecnico(), "RECORDATORIO",
                "A las " + venta.getHoraDesde().format(HORA) + " tenés un trabajo en " + donde, venta);
            venta.setRecordatorioEnviado(true);
            ventaRepo.save(venta);
            enviados++;
        }
        return enviados;
    }
}
