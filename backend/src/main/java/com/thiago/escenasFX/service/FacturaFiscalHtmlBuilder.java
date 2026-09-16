package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Arma el XHTML válido para las Facturas Fiscales electrónicas de ARCA, en el mismo formato
 * angosto que {@link TicketHtmlBuilder} — pensado para imprimirse en la Xprinter térmica del
 * mostrador, como una factura de cualquier facturadora fiscal de toda la vida (encabezado, ítems,
 * total, CAE y QR en una sola columna vertical), no como una hoja A4 formal. Incluye los campos
 * obligatorios: Letra del comprobante, CAE, Vencimiento y Código QR.
 *
 * <p>Antes de este cambio esta clase armaba una hoja A4 de dos columnas (logo a la izquierda,
 * recuadro de letra al medio, CUIT a la derecha) sin ningún {@code @page} — exactamente el mismo
 * bug que {@link TicketHtmlBuilder} explica en detalle (tamaño de página fijo real: 58mm de
 * ancho, 48mm imprimible, 210mm de largo — confirmado por el dueño en la impresora física).
 */
public final class FacturaFiscalHtmlBuilder {

    // Datos de la Distribuidora
    private static final String NOMBRE_LOCAL = "D13 Distribuidora";
    private static final String DIRECCION_LOCAL = "Arce 790, CABA";
    private static final String TELEFONO_LOCAL = "1123752626";
    // D13 es Monotributo (por eso todo lo que emite el sistema es Factura C, sin discriminar
    // IVA — ver FacturaFiscalService) — no "Responsable Inscripto" como decía antes acá.
    private static final String CONDICION_IVA = "Monotributo";

    private FacturaFiscalHtmlBuilder() {
    }

    public record Linea(String descripcion, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    /**
     * Construye el HTML de la Factura Fiscal, en el mismo ancho de ticket que
     * {@link TicketHtmlBuilder} y con su mismo {@code @page} fijo de 58×210mm.
     *
     * @param letraCmp Ej: "A", "B", "C"
     * @param qrBase64 Imagen del QR ya codificada en base64 (data:image/png;base64,...)
     * @param cuitFormateado CUIT del emisor con guiones (ej. "20-30023837-9"), el mismo que se usa
     *                       para autenticar y firmar ante AFIP — no una constante propia acá, para
     *                       que no pueda quedar desincronizado del CUIT real (ver FacturaPdfService).
     */
    public static String construir(String titulo, String letraCmp, List<String> infoCliente, List<Linea> items,
            BigDecimal total, String logoSrc, String cae, String vtoCae, String qrBase64, String cuitFormateado) {
        StringBuilder filas = new StringBuilder();
        for (Linea l : items) {
            filas.append("<div class='item'>")
                .append("<div class='item-desc'>").append(l.cantidad()).append("x ")
                .append(XmlEscaper.escape(l.descripcion())).append("</div>")
                .append("<table class='fila'><tr>")
                .append("<td>$").append(l.precioUnitario()).append(" c/u</td>")
                .append("<td class='derecha'>$").append(l.subtotal()).append("</td>")
                .append("</tr></table>")
                .append("</div>");
        }

        StringBuilder info = new StringBuilder();
        for (String linea : infoCliente) {
            info.append("<p class='info'>").append(XmlEscaper.escape(linea)).append("</p>");
        }

        return "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8'/>"
            + "<style>" + TicketHtmlBuilder.estilos() + estilosPropios() + "</style></head>"
            + "<body><div class='ticket'>"
            + "<div class='centro'><img src='" + logoSrc + "' class='logo'/></div>"
            + "<p class='centro negrita'>" + XmlEscaper.escape(NOMBRE_LOCAL) + "</p>"
            + "<p class='centro chico'>" + XmlEscaper.escape(DIRECCION_LOCAL) + " — Tel: "
            + XmlEscaper.escape(TELEFONO_LOCAL) + "</p>"
            + "<p class='centro chico'>CUIT: " + XmlEscaper.escape(cuitFormateado) + " — "
            + XmlEscaper.escape(CONDICION_IVA) + "</p>"
            + "<div class='separador'></div>"
            + "<div class='centro'>"
            + "<span class='letra'>" + XmlEscaper.escape(letraCmp) + "</span>"
            + "</div>"
            + "<p class='centro negrita'>" + XmlEscaper.escape(titulo) + "</p>"
            + "<div class='separador'></div>"
            + info
            + "<div class='separador'></div>"
            + filas
            + "<div class='separador'></div>"
            + "<table class='fila total'><tr><td>TOTAL</td><td class='derecha'>$" + total + "</td></tr></table>"
            + "<div class='separador'></div>"
            + "<p class='chico centro'>Comprobante autorizado por ARCA</p>"
            + "<p class='chico centro'>CAE: " + XmlEscaper.escape(cae) + "</p>"
            + "<p class='chico centro'>Vto. CAE: " + XmlEscaper.escape(vtoCae) + "</p>"
            + "<div class='centro'><img src='" + qrBase64 + "' class='qr'/></div>"
            + "</div></body></html>";
    }

    private static String estilosPropios() {
        return ".letra { display:inline-block; border:2px solid #000; width:10mm; height:10mm; "
            + "line-height:10mm; font-size:18px; font-weight:bold; text-align:center; }"
            + ".qr { width:38mm; height:38mm; margin-top:4px; }";
    }
}
