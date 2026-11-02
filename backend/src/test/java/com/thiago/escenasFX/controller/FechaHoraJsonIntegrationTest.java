package com.thiago.escenasFX.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * Usa el ObjectMapper real de la aplicación. Con -Duser.timezone=UTC reproduce a Railway: la venta
 * guardada a las 22:05 tiene que salir a las 19:05.
 */
class FechaHoraJsonIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void localDateTime_seSerializaEnHoraDeArgentina() throws Exception {
        LocalDateTime enArgentina = LocalDateTime.of(2026, 11, 2, 19, 5, 0);
        LocalDateTime guardada = enArgentina.atZone(ARGENTINA)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();

        assertThat(objectMapper.writeValueAsString(guardada)).isEqualTo("\"2026-11-02T19:05:00\"");
    }
}
