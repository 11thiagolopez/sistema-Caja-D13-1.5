package com.thiago.escenasFX.service;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.thiago.escenasFX.exception.AfipIntegracionException;

/**
 * WSFEv1 (facturación electrónica ARCA): solo las dos operaciones que usa el sistema —
 * FECompUltimoAutorizado (siguiente número disponible) y FECAESolicitar (emitir y obtener el
 * CAE). Arma los sobres SOAP a mano en vez de generar stubs JAX-WS desde el WSDL completo, mismo
 * criterio de simplicidad que CotizacionApiClient con las APIs REST de cotización.
 */
@Service
public class AfipFacturacionService {

    private static final String WSFE_URL = "https://servicios1.afip.gov.ar/wsfev1/service.asmx";
    private static final String NS = "http://ar.gov.afip.dif.FEV1/";
    private static final DateTimeFormatter FECHA_AFIP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final AfipAuthService authService;
    private final String cuit;

    public AfipFacturacionService(RestClient.Builder builder, AfipAuthService authService,
            @Value("${afip.cuit}") String cuit) {
        this.restClient = builder.build();
        this.authService = authService;
        this.cuit = cuit;
    }

    /** Concepto AFIP: 1 = Productos, 2 = Servicios, 3 = Productos y Servicios. */
    public record DatosFactura(int puntoVenta, int cbteTipo, int concepto, int docTipo, String docNro,
            BigDecimal importe, LocalDate fecha, LocalDate fchServDesde, LocalDate fchServHasta,
            LocalDate fchVtoPago) {
    }

    public record ResultadoCae(boolean aprobado, Integer numero, String cae, LocalDate caeVencimiento,
            String detalle) {
    }

    public int consultarUltimoAutorizado(int puntoVenta, int cbteTipo) {
        AfipAuthService.Credenciales cred = authService.obtenerCredenciales();
        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ar=\"" + NS + "\">"
            + "<soapenv:Header/>"
            + "<soapenv:Body>"
            + "<ar:FECompUltimoAutorizado>"
            + auth(cred)
            + "<ar:PtoVta>" + puntoVenta + "</ar:PtoVta>"
            + "<ar:CbteTipo>" + cbteTipo + "</ar:CbteTipo>"
            + "</ar:FECompUltimoAutorizado>"
            + "</soapenv:Body>"
            + "</soapenv:Envelope>";

        String respuesta = postSoap(envelope, NS + "FECompUltimoAutorizado");
        Document doc = parsearXml(respuesta);
        String cbteNro = textoDeEtiqueta(doc, "CbteNro");
        if (cbteNro == null) {
            throw new AfipIntegracionException("ARCA no devolvió CbteNro al consultar el último autorizado: " + respuesta);
        }
        return Integer.parseInt(cbteNro);
    }

    public ResultadoCae emitirFacturaC(DatosFactura datos) {
        int numero = consultarUltimoAutorizado(datos.puntoVenta(), datos.cbteTipo()) + 1;
        return solicitarCae(datos, numero);
    }

    ResultadoCae solicitarCae(DatosFactura datos, int numero) {
        AfipAuthService.Credenciales cred = authService.obtenerCredenciales();
        String envelope = construirEnvelopeFECAESolicitar(cred, datos, numero);
        String respuesta = postSoap(envelope, NS + "FECAESolicitar");
        return parsearResultadoCae(respuesta);
    }

    String construirEnvelopeFECAESolicitar(AfipAuthService.Credenciales cred, DatosFactura datos, int numero) {
        String importe = datos.importe().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        StringBuilder detalle = new StringBuilder();
        detalle.append("<ar:Concepto>").append(datos.concepto()).append("</ar:Concepto>")
            .append("<ar:DocTipo>").append(datos.docTipo()).append("</ar:DocTipo>")
            .append("<ar:DocNro>").append(datos.docNro()).append("</ar:DocNro>")
            .append("<ar:CbteDesde>").append(numero).append("</ar:CbteDesde>")
            .append("<ar:CbteHasta>").append(numero).append("</ar:CbteHasta>")
            .append("<ar:CbteFch>").append(datos.fecha().format(FECHA_AFIP)).append("</ar:CbteFch>")
            .append("<ar:ImpTotal>").append(importe).append("</ar:ImpTotal>")
            .append("<ar:ImpTotConc>0.00</ar:ImpTotConc>")
            .append("<ar:ImpNeto>").append(importe).append("</ar:ImpNeto>")
            .append("<ar:ImpOpEx>0.00</ar:ImpOpEx>")
            .append("<ar:ImpIVA>0.00</ar:ImpIVA>")
            .append("<ar:ImpTrib>0.00</ar:ImpTrib>")
            .append("<ar:MonId>PES</ar:MonId>")
            .append("<ar:MonCotiz>1</ar:MonCotiz>");
        if (datos.concepto() != 1) {
            detalle.append("<ar:FchServDesde>").append(datos.fchServDesde().format(FECHA_AFIP)).append("</ar:FchServDesde>")
                .append("<ar:FchServHasta>").append(datos.fchServHasta().format(FECHA_AFIP)).append("</ar:FchServHasta>")
                .append("<ar:FchVtoPago>").append(datos.fchVtoPago().format(FECHA_AFIP)).append("</ar:FchVtoPago>");
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ar=\"" + NS + "\">"
            + "<soapenv:Header/>"
            + "<soapenv:Body>"
            + "<ar:FECAESolicitar>"
            + auth(cred)
            + "<ar:FeCAEReq>"
            + "<ar:FeCabReq><ar:CantReg>1</ar:CantReg><ar:PtoVta>" + datos.puntoVenta()
            + "</ar:PtoVta><ar:CbteTipo>" + datos.cbteTipo() + "</ar:CbteTipo></ar:FeCabReq>"
            + "<ar:FeDetReq><ar:FECAEDetRequest>" + detalle + "</ar:FECAEDetRequest></ar:FeDetReq>"
            + "</ar:FeCAEReq>"
            + "</ar:FECAESolicitar>"
            + "</soapenv:Body>"
            + "</soapenv:Envelope>";
    }

    ResultadoCae parsearResultadoCae(String respuestaSoap) {
        Document doc = parsearXml(respuestaSoap);

        NodeList detalles = doc.getElementsByTagName("FECAEDetResponse");
        if (detalles.getLength() == 0) {
            String errorGeneral = extraerError(doc);
            throw new AfipIntegracionException("ARCA no devolvió detalle de comprobante"
                + (errorGeneral != null ? ": " + errorGeneral : ": " + respuestaSoap));
        }
        Element detalleResp = (Element) detalles.item(0);

        String resultado = textoDeEtiquetaEnElemento(detalleResp, "Resultado");
        boolean aprobado = "A".equals(resultado);

        if (!aprobado) {
            String motivo = extraerObservaciones(detalleResp);
            if (motivo == null) {
                motivo = extraerError(doc);
            }
            return new ResultadoCae(false, null, null, null,
                motivo != null ? motivo : "ARCA rechazó el comprobante (resultado " + resultado + ")");
        }

        String cae = textoDeEtiquetaEnElemento(detalleResp, "CAE");
        String vencimiento = textoDeEtiquetaEnElemento(detalleResp, "CAEFchVto");
        String cbteDesde = textoDeEtiquetaEnElemento(detalleResp, "CbteDesde");

        return new ResultadoCae(true, cbteDesde != null ? Integer.parseInt(cbteDesde) : null, cae,
            vencimiento != null ? LocalDate.parse(vencimiento, FECHA_AFIP) : null, null);
    }

    private String extraerError(Document doc) {
        NodeList errores = doc.getElementsByTagName("Err");
        if (errores.getLength() == 0) {
            return null;
        }
        Element err = (Element) errores.item(0);
        return textoDeEtiquetaEnElemento(err, "Code") + " - " + textoDeEtiquetaEnElemento(err, "Msg");
    }

    private String extraerObservaciones(Element detalleResp) {
        NodeList obs = detalleResp.getElementsByTagName("Obs");
        if (obs.getLength() == 0) {
            return null;
        }
        Element primera = (Element) obs.item(0);
        return textoDeEtiquetaEnElemento(primera, "Code") + " - " + textoDeEtiquetaEnElemento(primera, "Msg");
    }

    private String auth(AfipAuthService.Credenciales cred) {
        return "<ar:Auth><ar:Token>" + cred.token() + "</ar:Token><ar:Sign>" + cred.sign()
            + "</ar:Sign><ar:Cuit>" + cuit + "</ar:Cuit></ar:Auth>";
    }

    private String postSoap(String envelope, String soapAction) {
        try {
            String respuesta = restClient.post()
                .uri(WSFE_URL)
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", soapAction)
                .body(envelope)
                .retrieve()
                .body(String.class);
            if (respuesta == null) {
                throw new AfipIntegracionException("WSFE respondió vacío");
            }
            return respuesta;
        } catch (AfipIntegracionException e) {
            throw e;
        } catch (Exception e) {
            throw new AfipIntegracionException("No se pudo conectar con WSFE (ARCA): " + e.getMessage(), e);
        }
    }

    private Document parsearXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new AfipIntegracionException("No se pudo parsear la respuesta XML de ARCA", e);
        }
    }

    private String textoDeEtiqueta(Document doc, String tagName) {
        NodeList nodos = doc.getElementsByTagName(tagName);
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent();
    }

    private String textoDeEtiquetaEnElemento(Element elemento, String tagName) {
        NodeList nodos = elemento.getElementsByTagName(tagName);
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent();
    }
}
