package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Sin red: solo que el cliente S3 se arme con las variables de Railway y firme links con la forma correcta. */
class S3AlmacenamientoArchivosTest {

    @Test
    void conLasVariablesDeRailway_firmaUnLinkVirtualHosted() throws Exception {
        S3AlmacenamientoArchivos s3 = new S3AlmacenamientoArchivos("mi-bucket-abc123", "https://t3.storageapi.dev", "auto",
            "clave-falsa", "secreto-falso", false);
        try {
            String url = s3.urlTemporal("trabajos/7/x.mp4", Duration.ofHours(2));
            assertThat(url).startsWith("https://mi-bucket-abc123.t3.storageapi.dev/trabajos/7/x.mp4?")
                .contains("X-Amz-Signature=").contains("X-Amz-Expires=7200");
        } finally {
            s3.destroy();
        }
    }

    @Test
    void conPathStyle_elBucketVaEnLaRuta() throws Exception {
        S3AlmacenamientoArchivos s3 = new S3AlmacenamientoArchivos("viejo", "https://t3.storageapi.dev", "auto",
            "clave-falsa", "secreto-falso", true);
        try {
            assertThat(s3.urlTemporal("a.mp4", Duration.ofMinutes(5))).startsWith("https://t3.storageapi.dev/viejo/a.mp4?");
        } finally {
            s3.destroy();
        }
    }

    @Test
    void sinVariables_arrancaPeroLosAdjuntosAvisanQueNoEstanConfigurados() {
        S3AlmacenamientoArchivos s3 = new S3AlmacenamientoArchivos("", "https://t3.storageapi.dev", "auto", "", "", false);
        assertThatThrownBy(() -> s3.urlTemporal("a.mp4", Duration.ofMinutes(5)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no están configurados");
    }
}
