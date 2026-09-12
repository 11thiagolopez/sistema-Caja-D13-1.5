package com.thiago.escenasFX.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.thiago.escenasFX.model.Producto;

/**
 * Alertas de stock bajo por email al ADMIN. El cruce de umbral se detecta comparando el stock
 * antes/después del descuento en el mismo momento en que VentaService lo aplica (no se guarda
 * ningún flag "alerta_enviada_X" en Producto) — así una venta que baja el stock de 6 a 4 dispara
 * el aviso de 5 una sola vez, y ventas siguientes que lo sigan bajando (4, 3, 2...) no repiten ese
 * mismo aviso hasta cruzar el próximo umbral real.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private static final int UMBRAL_AVISO = 5;
    private static final int UMBRAL_URGENTE = 2;

    private final EmailService emailService;

    public NotificacionService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Los dos umbrales se evalúan de forma independiente (no excluyente): una venta grande que
     * baja el stock de, por ejemplo, 6 a 1 cruza los dos de una sola vez y dispara ambos avisos.
     * Nunca propaga una excepción — si Resend falla (o no hay ningún ADMIN con email configurado),
     * la venta que disparó el descuento de stock tiene que completarse igual.
     */
    public void evaluarYNotificarStockBajo(Producto producto, int stockAntes, int stockDespues) {
        if (cruzaUmbral(stockAntes, stockDespues, UMBRAL_AVISO)) {
            notificar(producto, stockDespues, UMBRAL_AVISO, "Aviso");
        }
        if (cruzaUmbral(stockAntes, stockDespues, UMBRAL_URGENTE)) {
            notificar(producto, stockDespues, UMBRAL_URGENTE, "URGENTE");
        }
    }

    private boolean cruzaUmbral(int stockAntes, int stockDespues, int umbral) {
        return stockAntes > umbral && stockDespues <= umbral;
    }

    private void notificar(Producto producto, int stockActual, int umbral, String nivel) {
        String asunto = String.format("[%s] Stock bajo (%d u.) — %s", nivel, stockActual, producto.getDescripcion());
        String cuerpo = String.format(
            "%s de stock bajo.%n%n"
                + "Producto: %s%n"
                + "Código interno: %s%n"
                + "Stock actual: %d unidad(es)%n"
                + "Nivel de alerta: %s (umbral de %d unidades)",
            nivel.equals("URGENTE") ? "Alerta urgente" : "Aviso", producto.getDescripcion(),
            producto.getCodigoInterno(), stockActual, nivel, umbral);

        try {
            emailService.enviarAAdmins(asunto, cuerpo);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la alerta de stock bajo (nivel {}) del producto {}: {}",
                nivel, producto.getCodigoInterno(), e.getMessage(), e);
        }
    }
}
