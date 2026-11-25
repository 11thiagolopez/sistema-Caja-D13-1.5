package com.thiago.escenasFX.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas programadas (hoy: el recordatorio de 1h antes de RecordatorioTrabajoService). Se apagan
 * con {@code recordatorios.habilitados=false}: los tests de integración lo hacen para que una pasada
 * automática no meta avisos en medio de un test; el servicio igual se puede llamar a mano.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "recordatorios.habilitados", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
