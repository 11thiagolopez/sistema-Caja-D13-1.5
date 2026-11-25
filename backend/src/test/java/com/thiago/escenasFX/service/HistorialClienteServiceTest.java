package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HistorialClienteServiceTest {

    @Test
    void claveTelefono_ignoraFormatoYPrefijos() {
        assertThat(HistorialClienteService.claveTelefono("11 5555-1234")).isEqualTo("55551234");
        assertThat(HistorialClienteService.claveTelefono("+54 9 11 5555 1234")).isEqualTo("55551234");
        assertThat(HistorialClienteService.claveTelefono("1155551234")).isEqualTo("55551234");
        assertThat(HistorialClienteService.claveTelefono("1234")).isNull();
        assertThat(HistorialClienteService.claveTelefono(null)).isNull();
    }

    @Test
    void claveDireccion_ignoraMayusculasTildesYPuntuacion() {
        assertThat(HistorialClienteService.claveDireccion("Av. Rivadavia  5200, 3°B"))
            .isEqualTo(HistorialClienteService.claveDireccion("av rivadavia 5200 3 b"));
        assertThat(HistorialClienteService.claveDireccion("Echeverría 1500"))
            .isEqualTo(HistorialClienteService.claveDireccion("ECHEVERRIA 1500"));
        assertThat(HistorialClienteService.claveDireccion("  ")).isNull();
    }
}
