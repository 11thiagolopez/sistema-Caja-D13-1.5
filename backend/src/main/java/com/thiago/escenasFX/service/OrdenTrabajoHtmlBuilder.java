package com.thiago.escenasFX.service;

import com.thiago.escenasFX.model.Venta;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma el XHTML de la orden de trabajo a domicilio para la Xprinter térmica de 58mm (Etapa 1,
 * Parte 5 — ver plan-migracion.md sección 34). Es lo que el técnico se lleva impreso antes de
 * salir: cuándo, adónde, a quién llamar, qué hay que hacer y las observaciones del jefe, con
 * renglones para la firma de conformidad del cliente al terminar.
 *
 * <p>Reusa la hoja de estilos de {@link TicketHtmlBuilder} (misma medida de papel fija de
 * 58mm × 210mm, todo en negrita y negro puro — ver ahí el porqué) para no tener dos criterios de
 * impresión térmica que puedan divergir. No lleva la "X" del ticket: una orden de trabajo no es
 * un comprobante de venta, así que en ese lugar va el título "ORDEN DE TRABAJO" con el número.
 *
 * <p>Los artículos y la mano de obra van con precio (el dueño confirmó que Fabián sí lo ve). Un
 * trabajo recién agendado puede no tener ítems todavía: en ese caso se aclara y no hay total.
 */
final class OrdenTrabajoHtmlBuilder {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private OrdenTrabajoHtmlBuilder() {
    }

    static String construir(Venta venta, List<ComprobanteHtmlBuilder.Linea> items, BigDecimal total,
            String logoSrc) {
        StringBuilder datos = new StringBuilder();
        if (venta.getFechaProgramada() != null) {
            datos.append(dato("Fecha", venta.getFechaProgramada().format(FECHA)));
        }
        String franja = franjaHoraria(venta.getHoraDesde(), venta.getHoraHasta());
        if (franja != null) {
            datos.append(dato("Horario", franja));
        }
        if (venta.getEmpleadoTecnico() != null) {
            datos.append(dato("Técnico", venta.getEmpleadoTecnico().getNombre()));
        }

        StringBuilder cliente = new StringBuilder();
        cliente.append(dato("Cliente", venta.getClienteNombre()));
        cliente.append(dato("Tel", venta.getClienteTelefono()));
        cliente.append(dato("Dirección", venta.getDireccionTrabajo()));

        StringBuilder trabajo = new StringBuilder();
        trabajo.append(bloque("Trabajo", venta.getDescripcionTrabajo()));
        trabajo.append(bloque("Observaciones", venta.getObservacionesTecnico()));

        StringBuilder filas = new StringBuilder();
        if (items.isEmpty()) {
            filas.append("<p class='info'>Sin artículos ni mano de obra cargados todavía.</p>");
        } else {
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
            filas.append("<div class='separador'></div>")
                .append("<table class='fila total'><tr><td>TOTAL</td><td class='derecha'>$")
                .append(total).append("</td></tr></table>");
        }

        return "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8'/>"
            + "<style>" + TicketHtmlBuilder.estilos() + estilosPropios() + "</style></head>"
            + "<body><div class='ticket'>"
            + "<div class='centro'><img src='" + logoSrc + "' class='logo'/></div>"
            + "<p class='centro negrita nombre'>D13 Distribuidora</p>"
            + "<p class='centro chico'>Arce 790, CABA — Tel: 1123752626</p>"
            + "<div class='separador'></div>"
            + "<p class='centro titulo-orden'>ORDEN DE TRABAJO</p>"
            + "<p class='centro numero-orden'>N° " + venta.getIdVenta() + "</p>"
            + datos
            + "<div class='separador'></div>"
            + cliente
            + "<div class='separador'></div>"
            + trabajo
            + "<div class='separador'></div>"
            + filas
            + "<div class='separador'></div>"
            + "<p class='info'>Conformidad del cliente:</p>"
            + "<div class='firma'></div><p class='chico centro'>Firma</p>"
            + "<div class='firma'></div><p class='chico centro'>Aclaración</p>"
            + "<p class='chico centro nota'>Documento interno, no válido como factura.</p>"
            + "</div></body></html>";
    }

    /** "10:00 a 12:00", "desde las 10:00", "hasta las 12:00", o null si no hay ninguna hora. */
    static String franjaHoraria(LocalTime desde, LocalTime hasta) {
        if (desde != null && hasta != null) {
            return desde.format(HORA) + " a " + hasta.format(HORA);
        }
        if (desde != null) {
            return "desde las " + desde.format(HORA);
        }
        if (hasta != null) {
            return "hasta las " + hasta.format(HORA);
        }
        return null;
    }

    private static String dato(String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return "<p class='info'>" + etiqueta + ": " + XmlEscaper.escape(valor) + "</p>";
    }

    /** Etiqueta en su propio renglón y el texto abajo: la descripción y las observaciones suelen
     * ser largas, y en 48mm de ancho leen mejor así que pegadas a la etiqueta. */
    private static String bloque(String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return "<p class='info'>" + etiqueta + ":</p><p class='texto-largo'>" + XmlEscaper.escape(valor) + "</p>";
    }

    private static String estilosPropios() {
        return ".titulo-orden { font-size: 17px; margin-top: 4px; }"
            + ".numero-orden { font-size: 20px; margin-bottom: 4px; }"
            + ".texto-largo { font-size: 13px; margin: 2px 0 6px 0; white-space: pre-wrap; }"
            + ".firma { border-bottom: 2px solid #000; height: 12mm; margin-top: 4px; }";
    }
}
