package com.thiago.escenasFX.service;

/**
 * Escapa texto dinámico antes de insertarlo en el XHTML de los comprobantes (openhtmltopdf exige
 * XHTML válido — un nombre de cliente o una descripción con "&" o "<" rompería el parser si no se
 * escapa). Antes esta misma lógica estaba duplicada en ComprobanteHtmlBuilder y
 * FacturaFiscalHtmlBuilder.
 */
final class XmlEscaper {

    private XmlEscaper() {
    }

    static String escape(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
