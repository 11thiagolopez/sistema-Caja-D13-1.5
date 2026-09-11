package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Arma el XHTML válido para las Facturas Fiscales electrónicas de ARCA.
 * Incluye los campos obligatorios: Letra del comprobante, CAE, Vencimiento y Código QR.
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
     * Construye el HTML de la Factura Fiscal.
     * @param letraCmp Ej: "A", "B", "C"
     * @param qrBase64 Imagen del QR ya codificada en base64 (data:image/png;base64,...)
     * @param cuitFormateado CUIT del emisor con guiones (ej. "20-30023837-9"), el mismo que se usa
     *                       para autenticar y firmar ante AFIP — no una constante propia acá, para
     *                       que no pueda quedar desincronizado del CUIT real (ver FacturaPdfService).
     */
    public static String construir(String titulo, String letraCmp, List<String> infoCliente, List<Linea> items,
                                   BigDecimal total, String logoSrc, String cae, String vtoCae, String qrBase64,
                                   String cuitFormateado) {
        
        StringBuilder filas = new StringBuilder();
        for (Linea l : items) {
            filas.append("<tr>")
                .append("<td style='padding:6px;border:1px solid #ccc;text-align:center;'>").append(l.cantidad()).append("</td>")
                .append("<td style='padding:6px;border:1px solid #ccc;'>").append(XmlEscaper.escape(l.descripcion())).append("</td>")
                .append("<td style='padding:6px;border:1px solid #ccc;text-align:right;'>$").append(l.precioUnitario()).append("</td>")
                .append("<td style='padding:6px;border:1px solid #ccc;text-align:right;'>$").append(l.subtotal()).append("</td>")
                .append("</tr>");
        }

        StringBuilder info = new StringBuilder();
        for (String linea : infoCliente) {
            info.append("<p style='margin:2px 0;font-size:13px;'>").append(XmlEscaper.escape(linea)).append("</p>");
        }

        return "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8'/></head>"
            + "<body style='font-family:Arial,sans-serif;font-size:12px;'>"
            + "<div style='max-width:700px; margin:0 auto; border:1px solid #000; padding:20px;'>"
            
            // CABECERA (Logo, Datos Empresa y Recuadro de Letra)
            + "<table style='width:100%; border-bottom:2px solid #000; padding-bottom:10px; margin-bottom:10px;'>"
            + "<tr>"
            + "<td style='width:45%; vertical-align:top;'>"
            + "<img src='" + logoSrc + "' alt='" + XmlEscaper.escape(NOMBRE_LOCAL) + "' style='height:70px;' />"
            + "<p style='margin:4px 0;font-weight:bold;font-size:14px;'>" + XmlEscaper.escape(NOMBRE_LOCAL) + "</p>"
            + "<p style='margin:2px 0;'>" + XmlEscaper.escape(DIRECCION_LOCAL) + "</p>"
            + "<p style='margin:2px 0;'>Tel: " + XmlEscaper.escape(TELEFONO_LOCAL) + "</p>"
            + "<p style='margin:2px 0;'>Condición frente al IVA: <strong>" + XmlEscaper.escape(CONDICION_IVA) + "</strong></p>"
            + "</td>"
            
            // RECUADRO CENTRAL CON LA LETRA
            + "<td style='width:10%; text-align:center; vertical-align:top;'>"
            + "<div style='border:2px solid #000; width:45px; height:45px; margin:0 auto; font-size:30px; font-weight:bold; line-height:45px;'>" 
            + XmlEscaper.escape(letraCmp) 
            + "</div>"
            + "</td>"
            
            // DATOS DEL COMPROBANTE Y CUIT
            + "<td style='width:45%; vertical-align:top; text-align:right;'>"
            + "<h2 style='margin:0 0 10px 0;font-size:22px;'>" + XmlEscaper.escape(titulo) + "</h2>"
            + "<p style='margin:2px 0;'><strong>CUIT:</strong> " + XmlEscaper.escape(cuitFormateado) + "</p>"
            + "<p style='margin:2px 0;'><strong>Ingresos Brutos:</strong> " + XmlEscaper.escape(cuitFormateado) + "</p>"
            + "<p style='margin:2px 0;'><strong>Inicio de Actividades:</strong> 01/08/2023</p>"
            + "</td>"
            + "</tr>"
            + "</table>"
            
            // DATOS DEL CLIENTE
            + "<div style='background-color:#f9f9f9; padding:10px; border:1px solid #ccc; margin-bottom:15px;'>"
            + info
            + "</div>"
            
            // TABLA DE PRODUCTOS
            + "<table style='width:100%; border-collapse:collapse;'>"
            + "<thead style='background-color:#eee;'>"
            + "<tr>"
            + "<th style='padding:8px;border:1px solid #ccc;width:10%;'>Cant.</th>"
            + "<th style='padding:8px;border:1px solid #ccc;text-align:left;'>Descripción</th>"
            + "<th style='padding:8px;border:1px solid #ccc;width:20%;text-align:right;'>Precio Unit.</th>"
            + "<th style='padding:8px;border:1px solid #ccc;width:20%;text-align:right;'>Subtotal</th>"
            + "</tr>"
            + "</thead>"
            + "<tbody>" + filas + "</tbody>"
            + "</table>"
            
            // TOTAL
            + "<div style='text-align:right; margin-top:15px; padding:10px; background-color:#eee; border:1px solid #ccc;'>"
            + "<span style='font-size:18px;'><strong>TOTAL: $" + total + "</strong></span>"
            + "</div>"
            
            // PIE FISCAL (ARCA - CAE y QR)
            + "<table style='width:100%; margin-top:20px; border-top:2px solid #000; padding-top:15px;'>"
            + "<tr>"
            + "<td style='width:20%;'>"
            + "<img src='" + qrBase64 + "' alt='QR ARCA' style='width:130px; height:130px;' />"
            + "</td>"
            + "<td style='vertical-align:middle; text-align:right;'>"
            + "<h3 style='margin:0 0 5px 0; color:#333;'>Comprobante Autorizado por ARCA</h3>"
            + "<p style='margin:5px 0; font-size:16px;'><strong>CAE:</strong> " + XmlEscaper.escape(cae) + "</p>"
            + "<p style='margin:5px 0; font-size:14px;'><strong>Fecha Vto. CAE:</strong> " + XmlEscaper.escape(vtoCae) + "</p>"
            + "</td>"
            + "</tr>"
            + "</table>"
            
            + "</div></body></html>";
    }
}