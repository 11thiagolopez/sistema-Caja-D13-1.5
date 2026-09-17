package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Arma el XHTML del ticket/remito pensado para imprimirse en la impresora térmica Xprinter del
 * local (mismo HTML que se descarga en PDF o se manda por mail desde Consulta de ventas). A
 * diferencia de {@link ComprobanteHtmlBuilder} (formato ancho tipo hoja A4, que sigue usando
 * Presupuestos para las cotizaciones que se mandan por mail a un cliente — no es algo que se
 * imprima en el mostrador), acá todo es una sola columna angosta: el papel térmico tiene ~48mm de
 * ancho imprimible real, así que una tabla con columnas Cantidad/Descripción/Precio/Subtotal lado
 * a lado no entra legible. Cada renglón se parte en dos líneas (descripción arriba,
 * cantidad×precio = subtotal abajo), como cualquier ticket de una facturadora fiscal de
 * mostrador.
 *
 * <p><b>Medida real del papel, confirmada por el dueño probando en la impresora física</b>: 58mm
 * de ancho de rollo, 48mm de ancho imprimible real, <b>210mm de largo fijo</b> — la Xprinter NO
 * está configurada en Windows como rollo continuo de alto variable, tiene un tamaño de página
 * fijo. Por eso el {@code @page} de acá es {@code size: 58mm 210mm} fijo, no un alto estimado
 * según la cantidad de renglones como en la primera versión de esta clase: declarar un alto más
 * chico que el que el driver de la impresora tiene configurado es lo que causaba que todo saliera
 * escalado a un cuadrado diminuto e ilegible (el driver ajusta el contenido a SU página
 * configurada, no a la del PDF). Con el tamaño fijo ya no hace falta estimar nada — sobra papel al
 * final si el ticket es corto, pero el contenido sale al tamaño real, legible.
 */
final class TicketHtmlBuilder {

    private TicketHtmlBuilder() {
    }

    static String construir(String titulo, List<String> infoLineas, List<ComprobanteHtmlBuilder.Linea> items,
            BigDecimal total, String notaFinal, String logoSrc) {
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
            + "<style>" + estilos() + "</style></head>"
            + "<body><div class='ticket'>"
            + "<div class='centro'><img src='" + logoSrc + "' class='logo'/></div>"
            + "<p class='centro negrita nombre'>D13 Distribuidora</p>"
            + "<p class='centro chico'>Arce 790, CABA — Tel: 1123752626</p>"
            + "<div class='separador'></div>"
            + "<p class='negrita titulo'>" + XmlEscaper.escape(titulo) + "</p>"
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
     * .negrita, .chico, .separador, .fila, .derecha) más las suyas propias para CAE/QR.
     *
     * <p>Tamaños de fuente deliberadamente grandes: la primera versión (8-12px) salió ilegible en
     * la impresora real ("como un cuadrado chico"), y con el alto de página ahora fijo en 210mm
     * sobra espacio de sobra para agrandar todo sin miedo a que no entre.
     *
     * <p><b>Todo en negrita y negro puro</b>: agrandar la letra (arriba) no alcanzó — el dueño la
     * probó impresa de vuelta y la tipografía fina salía gris y punteada en el cabezal térmico
     * (resolución baja, un trazo fino no genera suficientes puntos negros continuos para leerse
     * bien). {@code body} entero en {@code font-weight:bold} en vez de negritar clase por clase —
     * ninguna clase de acá pisa eso con {@code normal} — y {@code .chico} pasó de {@code #333}
     * (gris) a {@code #000} (negro puro), mismo criterio que ya tenía el resto del texto. */
    static String estilos() {
        // "body, p, div, table, td { margin:0; padding:0; }": sin este reset, cada <p> conserva
        // el margen por defecto del user-agent y el contenido corre más de lo esperado — ya no es
        // crítico para el alto (que ahora es fijo), pero se mantiene para que el espaciado salga
        // predecible y controlado solo por las clases de acá.
        return "@page { size: 58mm 210mm; margin: 5mm; }"
            + "body, p, div, table, td { margin:0; padding:0; }"
            + "body { font-family: Arial, sans-serif; font-size: 14px; line-height: 1.35; "
            + "font-weight: bold; color: #000; }"
            + ".ticket { width: 48mm; }"
            + ".centro { text-align:center; }"
            + ".negrita { font-weight:bold; }"
            + ".nombre { font-size: 16px; }"
            + ".titulo { font-size: 15px; }"
            + ".chico { font-size: 13px; color:#000; }"
            + ".logo { max-width: 40mm; max-height: 18mm; }"
            + ".separador { border-top: 2px dashed #000; margin: 6px 0; }"
            + ".info { margin: 3px 0; font-size: 12px; }"
            + ".item { margin: 6px 0; }"
            + ".item-desc { font-size: 13px; margin-bottom: 2px; }"
            + ".fila { width:100%; font-size: 13px; border-collapse:collapse; }"
            + ".derecha { text-align:right; }"
            + ".total { font-size: 20px; margin-top:6px; }"
            + ".nota { margin-top:10px; font-size: 10px; }";
    }
}
