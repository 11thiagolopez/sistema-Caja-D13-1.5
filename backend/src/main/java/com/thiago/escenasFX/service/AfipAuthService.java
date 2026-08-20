package com.thiago.escenasFX.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.thiago.escenasFX.exception.AfipIntegracionException;

/**
 * WSAA (autenticación ARCA): firma un LoginTicketRequest con la clave privada del certificado
 * como mensaje CMS/PKCS7 y lo cambia por un Token+Sign válido ~12hs, cacheado en memoria (WSAA
 * rechaza logins demasiado frecuentes para el mismo servicio). Ver plan-migracion.md, sección de
 * facturación fiscal, para el detalle de por qué se arma el SOAP a mano en vez de generar stubs.
 */
@Service
public class AfipAuthService {

    private static final String WSAA_URL = "https://wsaa.afip.gov.ar/ws/services/LoginCms";
    private static final String SERVICIO = "wsfe";
    private static final String WSAA_NAMESPACE = "http://wsaa.view.sua.dvadac.afip.gov.ar/";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final RestClient restClient;
    private final String keyBase64;
    private final String keyPath;
    private final String crtBase64;
    private final String crtPath;

    private PrivateKey clavePrivadaCache;
    private X509Certificate certificadoCache;
    private Credenciales credencialesCache;
    private Instant credencialesVencenEn;

    public AfipAuthService(RestClient.Builder builder,
            @Value("${afip.cert.key-base64:}") String keyBase64,
            @Value("${afip.cert.key-path:}") String keyPath,
            @Value("${afip.cert.crt-base64:}") String crtBase64,
            @Value("${afip.cert.crt-path:}") String crtPath) {
        this.restClient = builder.build();
        this.keyBase64 = keyBase64;
        this.keyPath = keyPath;
        this.crtBase64 = crtBase64;
        this.crtPath = crtPath;
    }

    public record Credenciales(String token, String sign) {
    }

    public synchronized Credenciales obtenerCredenciales() {
        if (credencialesCache != null && Instant.now().isBefore(credencialesVencenEn)) {
            return credencialesCache;
        }

        String cms = firmarLoginTicketRequest();
        String respuestaSoap = llamarLoginCms(cms);
        credencialesCache = parsearCredenciales(respuestaSoap);
        // Margen de seguridad: se vuelve a pedir 5 minutos antes de que WSAA lo dé por vencido.
        credencialesVencenEn = parsearVencimiento(respuestaSoap).minusSeconds(300);
        return credencialesCache;
    }

    String construirLoginTicketRequestXml(ZonedDateTime ahora) {
        DateTimeFormatter iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String generacion = ahora.minusMinutes(10).format(iso);
        String expiracion = ahora.plusMinutes(10).format(iso);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<loginTicketRequest version=\"1.0\">"
            + "<header>"
            + "<uniqueId>" + ahora.toEpochSecond() + "</uniqueId>"
            + "<generationTime>" + generacion + "</generationTime>"
            + "<expirationTime>" + expiracion + "</expirationTime>"
            + "</header>"
            + "<service>" + SERVICIO + "</service>"
            + "</loginTicketRequest>";
    }

    private String firmarLoginTicketRequest() {
        String xml = construirLoginTicketRequestXml(ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")));
        return firmarCms(xml);
    }

    private String firmarCms(String xml) {
        try {
            PrivateKey clavePrivada = clavePrivada();
            X509Certificate certificado = certificado();

            ContentSigner firmante = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(clavePrivada);

            CMSSignedDataGenerator generador = new CMSSignedDataGenerator();
            generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(firmante, certificado));
            generador.addCertificates(new JcaCertStore(java.util.List.of(certificado)));

            CMSTypedData contenido = new CMSProcessableByteArray(xml.getBytes(StandardCharsets.UTF_8));
            CMSSignedData firmado = generador.generate(contenido, true);
            return Base64.getEncoder().encodeToString(firmado.getEncoded());
        } catch (OperatorCreationException | java.security.cert.CertificateEncodingException | org.bouncycastle.cms.CMSException | IOException e) {
            throw new AfipIntegracionException("No se pudo firmar el pedido de login a ARCA (CMS)", e);
        }
    }

    private String llamarLoginCms(String cmsBase64) {
        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
            + "xmlns:wsaa=\"" + WSAA_NAMESPACE + "\">"
            + "<soapenv:Header/>"
            + "<soapenv:Body>"
            + "<wsaa:loginCms><wsaa:in0>" + cmsBase64 + "</wsaa:in0></wsaa:loginCms>"
            + "</soapenv:Body>"
            + "</soapenv:Envelope>";

        try {
            String respuesta = restClient.post()
                .uri(WSAA_URL)
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", "")
                .body(envelope)
                .retrieve()
                .body(String.class);
            if (respuesta == null) {
                throw new AfipIntegracionException("WSAA respondió vacío");
            }
            return respuesta;
        } catch (AfipIntegracionException e) {
            throw e;
        } catch (Exception e) {
            throw new AfipIntegracionException("No se pudo conectar con WSAA (login ARCA): " + e.getMessage(), e);
        }
    }

    Credenciales parsearCredenciales(String respuestaSoap) {
        Document loginTicketResponse = parsearLoginTicketResponse(respuestaSoap);
        String token = textoDeEtiqueta(loginTicketResponse, "token");
        String sign = textoDeEtiqueta(loginTicketResponse, "sign");
        if (token == null || sign == null) {
            throw new AfipIntegracionException("WSAA no devolvió token/sign: " + respuestaSoap);
        }
        return new Credenciales(token, sign);
    }

    Instant parsearVencimiento(String respuestaSoap) {
        Document loginTicketResponse = parsearLoginTicketResponse(respuestaSoap);
        String expirationTime = textoDeEtiqueta(loginTicketResponse, "expirationTime");
        if (expirationTime == null) {
            // No debería pasar si el login fue exitoso; margen conservador de 11hs si faltara.
            return Instant.now().plusSeconds(11 * 3600);
        }
        return ZonedDateTime.parse(expirationTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    }

    private Document parsearLoginTicketResponse(String respuestaSoap) {
        Document sobre = parsearXml(respuestaSoap);
        String loginCmsReturn = textoDeEtiqueta(sobre, "loginCmsReturn");
        if (loginCmsReturn == null) {
            throw new AfipIntegracionException("Respuesta de WSAA sin loginCmsReturn: " + respuestaSoap);
        }
        return parsearXml(loginCmsReturn);
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
        var nodos = doc.getElementsByTagName(tagName);
        if (nodos.getLength() == 0) {
            return null;
        }
        return ((Element) nodos.item(0)).getTextContent();
    }

    private PrivateKey clavePrivada() {
        if (clavePrivadaCache != null) {
            return clavePrivadaCache;
        }
        String pem = !keyBase64.isBlank()
            ? new String(Base64.getDecoder().decode(keyBase64), StandardCharsets.UTF_8)
            : leerArchivo(keyPath, "AFIP_CERT_KEY_BASE64 / afip.cert.key-path");
        clavePrivadaCache = parsearClavePrivada(pem);
        return clavePrivadaCache;
    }

    private X509Certificate certificado() {
        if (certificadoCache != null) {
            return certificadoCache;
        }
        String pem = !crtBase64.isBlank()
            ? new String(Base64.getDecoder().decode(crtBase64), StandardCharsets.UTF_8)
            : leerArchivo(crtPath, "AFIP_CERT_CRT_BASE64 / afip.cert.crt-path");
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            certificadoCache = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            return certificadoCache;
        } catch (CertificateException e) {
            throw new AfipIntegracionException("No se pudo leer el certificado de ARCA", e);
        }
    }

    private String leerArchivo(String path, String origenEsperado) {
        if (path.isBlank()) {
            throw new AfipIntegracionException(
                "Falta configurar el certificado de ARCA (" + origenEsperado + ")");
        }
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new AfipIntegracionException("No se pudo leer " + path, e);
        }
    }

    private PrivateKey parsearClavePrivada(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object objeto = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (objeto instanceof PEMKeyPair pemKeyPair) {
                return converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
            }
            if (objeto instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            if (objeto instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new AfipIntegracionException("La clave privada de ARCA está encriptada con passphrase, no soportado");
            }
            throw new AfipIntegracionException("Formato de clave privada de ARCA no reconocido: " + objeto);
        } catch (IOException e) {
            throw new AfipIntegracionException("No se pudo leer la clave privada de ARCA", e);
        }
    }
}
