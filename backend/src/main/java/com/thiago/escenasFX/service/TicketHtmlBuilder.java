package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Arma el XHTML del ticket/remito pensado para imprimirse en la impresora térmica Xprinter de
 * 58mm del local (mismo HTML que se descarga en PDF o se manda por mail desde Consulta de
 * ventas). A diferencia de {@link ComprobanteHtmlBuilder} (formato ancho tipo hoja A4, que sigue
 * usando Presupuestos para las cotizaciones que se mandan por mail a un cliente — no es algo que
 * se imprima en el mostrador), acá todo es una sola columna angosta: el papel térmico de 58mm
 * tiene ~54mm de ancho imprimible, así que una tabla con columnas
 * Cantidad/Descripción/Precio/Subtotal lado a lado no entra legible. Cada renglón se parte en dos
 * líneas (descripción arriba, cantidad×precio = subtotal abajo), como cualquier ticket de una
 * facturadora fiscal de mostrador.
 *
 * <p>Bug real que esta clase soluciona: ni acá ni en {@link FacturaFiscalHtmlBuilder} (antes de
 * este mismo cambio) había un {@code @page} en el HTML, así que el PDF salía con el tamaño de
 * página por defecto de openhtmltopdf (carta/A4) — al mandarlo a imprimir en la Xprinter 58mm, el
 * driver de Windows o escala esa hoja entera al ancho de 58mm (todo el contenido, texto incluido,
 * termina en un cuadrado minúsculo) o la imprime a tamaño real sobre el rollo continuo (una
 * impresión larguísima, mayormente en blanco, porque una hoja carta mide ~279mm de alto). Acá se
 * declara explícitamente {@code size: 58mm <alto>mm} — a diferencia del ticket que se imprime
 * directo desde el navegador (ver App.css/.comprobante), un PDF no tiene noción de "alto
 * automático": hay que declarar un alto fijo, estimado según la cantidad real de renglones para
 * no dejar metros de papel en blanco ni cortar contenido.
 */
final class TicketHtmlBuilder {

    private TicketHtmlBuilder() {
    }

    /**
     * Cada ítem ocupa dos líneas (descripción + cantidad×precio), cada línea de info una sola. Los
     * ~50mm base cubren logo, nombre/dirección/teléfono del local, título, separadores, total y
     * nota final. Piso de 65mm para que un ticket con pocos ítems no quede con menos alto del que
     * ya ocupa el encabezado.
     *
     * <p>Constantes deliberadamente generosas (con margen de sobra, no ajustadas al límite):
     * mejor un poco de papel de más al final que un renglón cortado a una página nueva — un PDF a
     * diferencia del {@code @media print} del navegador no tiene forma de "seguir en la misma
     * hoja" si el contenido no entra. Verificado a mano generando PDFs de muestra con contenido
     * real (ver MuestraImpresion58mmTest, no forma parte de la suite permanente) — la primera
     * estimación (38 + 4/línea + 8/ítem) se quedaba corta y partía el ticket en dos páginas.
     */
    static int estimarAltoMm(int cantidadItems, int cantidadInfo) {
        int alto = 50 + cantidadInfo * 6 + cantidadItems * 11;
        return Math.max(alto, 65);
    }

    static String construir(String titulo, List<String> infoLineas, List<ComprobanteHtmlBuilder.Linea> items,
            BigDecimal total, String notaFinal, String logoSrc) {
        int altoMm = estimarAltoMm(items.size(), infoLineas.size());

        StringBuilder filas = new StringBuilder();
        for (ComprobanteHtmlBuilder.Linea l : items) {
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
        for (String linea : infoLineas) {
            info.append("<p class='info'>").append(XmlEscaper.escape(linea)).append("</p>");
        }

        return "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8'/>"
            + "<style>" + estilos(altoMm) + "</style></head>"
            + "<body><div class='ticket'>"
            + "<div class='centro'><img src='" + logoSrc + "' class='logo'/></div>"
            + "<p class='centro negrita'>D13 Distribuidora</p>"
            + "<p class='centro chico'>Arce 790, CABA — Tel: 1123752626</p>"
            + "<div class='separador'></div>"
            + "<p class='negrita'>" + XmlEscaper.escape(titulo) + "</p>"
            + info
            + "<div class='separador'></div>"
            + filas
            + "<div class='separador'></div>"
            + "<table class='fila total'><tr><td>TOTAL</td><td class='derecha'>$" + total + "</td></tr></table>"
            + (notaFinal != null && !notaFinal.isBlank()
                ? "<p class='chico centro nota'>" + XmlEscaper.escape(notaFinal) + "</p>"
                : "")
            + "</div></body></html>";
    }

    /** Compartido con {@link FacturaFiscalHtmlBuilder}, que usa las mismas clases base (.centro,
     * .negrita, .chico, .separador, .fila, .derecha) más las suyas propias para CAE/QR. */
    static String estilos(int altoMm) {
        // "body, p, div, table, td { margin:0; padding:0; }": sin este reset, cada <p> conserva
        // el margen por defecto del user-agent (~1em arriba y abajo) y el alto real termina
        // siendo mayor al estimado — la causa concreta de que la primera versión de esta clase
        // partiera el ticket en dos páginas. Con el reset, todo el espaciado sale explícito de
        // las clases de acá (line-height fijo, márgenes en mm por clase), así que estimarAltoMm
        // puede confiar en sus propias constantes.
        return "@page { size: 58mm " + altoMm + "mm; margin: 2mm; }"
            + "body, p, div, table, td { margin:0; padding:0; }"
            + "body { font-family: Arial, sans-serif; font-size: 10px; line-height: 1.3; }"
            + ".ticket { width: 54mm; }"
            + ".centro { text-align:center; }"
            + ".negrita { font-weight:bold; }"
            + ".chico { font-size: 8px; color:#333; }"
            + ".logo { max-width: 34mm; max-height: 14mm; }"
            + ".separador { border-top: 1px dashed #000; margin: 4px 0; }"
            + ".info { margin: 2px 0; font-size: 9px; }"
            + ".item { margin: 4px 0; }"
            + ".item-desc { font-size: 9px; font-weight:bold; margin-bottom: 1px; }"
            + ".fila { width:100%; font-size: 9px; border-collapse:collapse; }"
            + ".derecha { text-align:right; }"
            + ".total { font-size: 12px; font-weight:bold; margin-top:4px; }"
            + ".nota { margin-top:8px; }";
    }
}
