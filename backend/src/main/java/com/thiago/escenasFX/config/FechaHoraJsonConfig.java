package com.thiago.escenasFX.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Los LocalDateTime se guardan con LocalDateTime.now(), en la zona de la JVM (UTC en Railway). Sin
 * esto, la API los manda tal cual y una venta de las 19:05 se ve a las 22:05 en todas las pantallas.
 * Convertir al serializar los deja en hora de Argentina sin tocar los datos guardados, y sigue dando
 * bien si la JVM cambia de zona.
 */
@Configuration
public class FechaHoraJsonConfig {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer fechaHoraEnZonaDelNegocio() {
        return builder -> builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime valor, JsonGenerator generador, SerializerProvider proveedor)
                    throws IOException {
                LocalDateTime enArgentina = valor.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZONA_NEGOCIO)
                    .toLocalDateTime();
                generador.writeString(enArgentina.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        });
    }
}
