package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Wrapper fino de las dos APIs públicas de cotización del dólar oficial (Banco Nación). Nunca
 * propaga: cualquier falla (timeout, HTTP no-2xx, parseo) se traduce en Optional.empty() para que
 * CotizacionService decida el fallback.
 */
@Component
public class CotizacionApiClient {

    private final RestClient restClient;
    private final String urlPrimaria;
    private final String urlSecundaria;

    public CotizacionApiClient(RestClient.Builder builder,
            @Value("${cotizacion.dolar.api-primaria}") String urlPrimaria,
            @Value("${cotizacion.dolar.api-secundaria}") String urlSecundaria,
            @Value("${cotizacion.dolar.timeout-ms:5000}") int timeoutMs) {
        this.urlPrimaria = urlPrimaria;
        this.urlSecundaria = urlSecundaria;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    public Optional<BigDecimal> consultarPrimaria() {
        return consultar(urlPrimaria, DolarApiResponse.class, DolarApiResponse::getVenta);
    }

    public Optional<BigDecimal> consultarSecundaria() {
        return consultar(urlSecundaria, DolarBnaResponse.class, DolarBnaResponse::getVenta);
    }

    private <T> Optional<BigDecimal> consultar(String url, Class<T> tipoRespuesta, Function<T, BigDecimal> extractor) {
        try {
            T respuesta = restClient.get().uri(url).retrieve().body(tipoRespuesta);
            return respuesta == null ? Optional.empty() : Optional.ofNullable(extractor.apply(respuesta));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DolarApiResponse {
        private BigDecimal venta;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DolarBnaResponse {
        private BigDecimal venta;
    }
}
