package com.thiago.escenasFX.service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.thiago.escenasFX.exception.AfipIntegracionException;

/**
 * Padrón de ARCA (ws_sr_constancia_inscripcion, getPersona_v2): devuelve el nombre con el que está
 * registrado un CUIT, para autocompletarlo al facturar. Es solo una consulta, no emite nada. Mismo
 * criterio que AfipFacturacionService: sobre SOAP armado a mano. Contrato según el manual oficial
 * del servicio, v3.4. El certificado tiene que estar autorizado para este servicio en ARCA, aparte
 * de wsfe.
 */
@Service
public class AfipPadronService {

    private static final String PADRON_URL = "https://aws.afip.gov.ar/sr-padron/webservices/personaServiceA5";
    private static final String NS = "http://a5.soap.ws.server.puc.sr/";
    private static final int[] PESOS_CUIT = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final int CONDICION_RESPONSABLE_INSCRIPTO = 1;
    private static final int CONDICION_EXENTO = 4;
    private static final int CONDICION_MONOTRIBUTO = 6;
    private static final String IMPUESTO_IVA = "30";
    private static final String IMPUESTO_IVA_EXENTO = "32";

    private final RestClient restClient;
    private final AfipAuthService authService;
    private final String cuitRepresentada;

    public AfipPadronService(RestClient.Builder builder, AfipAuthService authService,
            @Value("${afip.cuit}") String cuitRepresentada) {
        this.restClient = builder.build();
        this.authService = authService;
        this.cuitRepresentada = cuitRepresentada;
    }

    /**
     * condicionIva es una sugerencia deducida de los impuestos inscriptos (null si no se puede
     * deducir). advertencia trae lo que ARCA objeta de la constancia (ej. CUIT limitada o
     * cancelada): null si no objeta nada.
     */
    public record Contribuyente(String cuit, String nombre, String tipoPersona, String domicilio,
            String estadoClave, Integer condicionIva, String advertencia) {
    }

    public Contribuyente consultar(String cuit) {
        if (!esCuitValido(cuit)) {
            throw new IllegalArgumentException("El CUIT " + cuit + " no es válido");
        }
        AfipAuthService.Credenciales cred = authService.obtenerCredenciales(AfipAuthService.SERVICIO_PADRON);
        return parsearPersona(postSoap(construirEnvelope(cred, cuit)), cuit);
    }

    static boolean esCuitValido(String cuit) {
        if (cuit == null || !cuit.matches("\\d{11}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < PESOS_CUIT.length; i++) {
            suma += Character.getNumericValue(cuit.charAt(i)) * PESOS_CUIT[i];
        }
        int resto = 11 - suma % 11;
        int verificador = resto == 11 ? 0 : resto;
        return verificador == Character.getNumericValue(cuit.charAt(10));
    }

    String construirEnvelope(AfipAuthService.Credenciales cred, String cuit) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:a5=\"" + NS + "\">"
            + "<soapenv:Header/>"
            + "<soapenv:Body>"
            + "<a5:getPersona_v2>"
            + "<token>" + cred.token() + "</token>"
            + "<sign>" + cred.sign() + "</sign>"
            + "<cuitRepresentada>" + cuitRepresentada + "</cuitRepresentada>"
            + "<idPersona>" + cuit + "</idPersona>"
            + "</a5:getPersona_v2>"
            + "</soapenv:Body>"
            + "</soapenv:Envelope>";
    }

    Contribuyente parsearPersona(String respuestaSoap, String cuit) {
        Document doc = parsearXml(respuestaSoap);

        String fault = textoDeEtiqueta(doc.getDocumentElement(), "faultstring");
        if (fault != null) {
            if (fault.toLowerCase().contains("no existe")) {
                throw new IllegalArgumentException("ARCA no tiene registrado el CUIT " + cuit);
            }
            throw new AfipIntegracionException("ARCA rechazó la consulta al padrón: " + fault);
        }

        NodeList personas = doc.getElementsByTagName("personaReturn");
        if (personas.getLength() == 0) {
            throw new AfipIntegracionException("ARCA no devolvió datos del CUIT " + cuit + ": " + respuestaSoap);
        }
        Element persona = (Element) personas.item(0);
        Element generales = primerElemento(persona, "datosGenerales");
        Element errorConstancia = primerElemento(persona, "errorConstancia");

        String advertencia = errorConstancia != null ? erroresDe(errorConstancia) : null;
        // Con la constancia objetada ARCA puede no mandar datosGenerales: el nombre viene ahí.
        Element fuenteNombre = generales != null ? generales : errorConstancia;
        String nombre = fuenteNombre != null ? nombreDe(fuenteNombre) : null;
        if (nombre == null) {
            if (advertencia != null && advertencia.toLowerCase().contains("no existe")) {
                throw new IllegalArgumentException("ARCA no tiene registrado el CUIT " + cuit);
            }
            throw new IllegalArgumentException("ARCA no informa un nombre para el CUIT " + cuit
                + (advertencia != null ? ": " + advertencia : ""));
        }

        return new Contribuyente(cuit, nombre,
            generales != null ? textoDeEtiqueta(generales, "tipoPersona") : null,
            generales != null ? domicilioDe(generales) : null,
            generales != null ? textoDeEtiqueta(generales, "estadoClave") : null,
            condicionIvaDe(persona), advertencia);
    }

    private String nombreDe(Element datos) {
        String razonSocial = textoDeEtiqueta(datos, "razonSocial");
        if (razonSocial != null) {
            return razonSocial;
        }
        String completo = Stream.of(textoDeEtiqueta(datos, "apellido"), textoDeEtiqueta(datos, "nombre"))
            .filter(parte -> parte != null)
            .collect(Collectors.joining(" "));
        return completo.isEmpty() ? null : completo;
    }

    private String domicilioDe(Element generales) {
        Element domicilio = primerElemento(generales, "domicilioFiscal");
        if (domicilio == null) {
            return null;
        }
        // ARCA suele rellenar la localidad con espacios y un asterisco (ej. "YAPEYU       *").
        String localidad = textoDeEtiqueta(domicilio, "localidad");
        String completo = Stream.of(textoDeEtiqueta(domicilio, "direccion"),
                localidad != null ? localidad.replace("*", "").trim() : null,
                textoDeEtiqueta(domicilio, "descripcionProvincia"))
            .filter(parte -> parte != null && !parte.isEmpty())
            .collect(Collectors.joining(", "));
        return completo.isEmpty() ? null : completo;
    }

    private Integer condicionIvaDe(Element persona) {
        if (primerElemento(persona, "datosMonotributo") != null) {
            return CONDICION_MONOTRIBUTO;
        }
        Element regimenGeneral = primerElemento(persona, "datosRegimenGeneral");
        if (regimenGeneral == null) {
            return null;
        }
        List<String> impuestos = new ArrayList<>();
        NodeList nodos = regimenGeneral.getElementsByTagName("idImpuesto");
        for (int i = 0; i < nodos.getLength(); i++) {
            impuestos.add(nodos.item(i).getTextContent().trim());
        }
        if (impuestos.contains(IMPUESTO_IVA)) {
            return CONDICION_RESPONSABLE_INSCRIPTO;
        }
        return impuestos.contains(IMPUESTO_IVA_EXENTO) ? CONDICION_EXENTO : null;
    }

    private String erroresDe(Element errorConstancia) {
        List<String> errores = new ArrayList<>();
        NodeList nodos = errorConstancia.getElementsByTagName("error");
        for (int i = 0; i < nodos.getLength(); i++) {
            errores.add(nodos.item(i).getTextContent().trim());
        }
        return errores.isEmpty() ? null : String.join("; ", errores);
    }

    private String postSoap(String envelope) {
        try {
            String respuesta = restClient.post()
                .uri(PADRON_URL)
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", "")
                .body(envelope)
                .retrieve()
                .body(String.class);
            if (respuesta == null) {
                throw new AfipIntegracionException("El padrón de ARCA respondió vacío");
            }
            return respuesta;
        } catch (RestClientResponseException e) {
            // Los SOAP fault llegan con un HTTP de error, pero su cuerpo dice el motivo real.
            String cuerpo = e.getResponseBodyAsString();
            if (cuerpo.contains("faultstring")) {
                return cuerpo;
            }
            throw new AfipIntegracionException("No se pudo consultar el padrón de ARCA: " + e.getMessage(), e);
        } catch (AfipIntegracionException e) {
            throw e;
        } catch (Exception e) {
            throw new AfipIntegracionException("No se pudo conectar con el padrón de ARCA: " + e.getMessage(), e);
        }
    }

    private Document parsearXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new AfipIntegracionException("No se pudo parsear la respuesta XML del padrón de ARCA", e);
        }
    }

    private Element primerElemento(Element padre, String tagName) {
        NodeList nodos = padre.getElementsByTagName(tagName);
        return nodos.getLength() == 0 ? null : (Element) nodos.item(0);
    }

    private String textoDeEtiqueta(Element elemento, String tagName) {
        NodeList nodos = elemento.getElementsByTagName(tagName);
        if (nodos.getLength() == 0) {
            return null;
        }
        String texto = nodos.item(0).getTextContent().trim();
        return texto.isEmpty() ? null : texto;
    }
}
