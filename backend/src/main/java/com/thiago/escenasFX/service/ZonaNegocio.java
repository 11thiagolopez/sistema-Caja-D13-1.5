package com.thiago.escenasFX.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Los días que se ven en pantalla son de Argentina y las fechas se guardan con LocalDateTime.now(),
 * en la zona de la JVM (UTC en Railway). Sin convertir los límites, lo cobrado después de las 21:00
 * cae en el día siguiente.
 */
public final class ZonaNegocio {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private ZonaNegocio() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public static LocalDate diaDe(LocalDateTime guardada) {
        return guardada.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZONA).toLocalDate();
    }

    public static LocalDateTime inicioDelDia(LocalDate dia) {
        return dia.atStartOfDay(ZONA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    // Para consultas BETWEEN, que incluyen los dos extremos. Postgres guarda hasta microsegundos.
    public static LocalDateTime finDelDia(LocalDate dia) {
        return inicioDelDia(dia.plusDays(1)).minus(1, ChronoUnit.MICROS);
    }
}
