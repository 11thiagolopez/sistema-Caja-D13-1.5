package com.thiago.escenasFX.service;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Archivos adjuntos (videos y audios) en el Railway Storage Bucket (API S3). Las credenciales salen de las variables que Railway
 * crea al conectar el bucket al servicio (ver application.properties). Si falta alguna, el backend
 * arranca igual y solo los adjuntos responden "no están configurados".
 */
@Service
@ConditionalOnProperty(name = "archivos.almacenamiento", havingValue = "s3", matchIfMissing = true)
public class S3AlmacenamientoArchivos implements AlmacenamientoArchivos, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(S3AlmacenamientoArchivos.class);

    private final String bucket;
    private final S3Client cliente;
    private final S3Presigner presigner;

    public S3AlmacenamientoArchivos(
            @Value("${archivos.s3.bucket:}") String bucket,
            @Value("${archivos.s3.endpoint:}") String endpoint,
            @Value("${archivos.s3.region:auto}") String region,
            @Value("${archivos.s3.access-key-id:}") String accessKeyId,
            @Value("${archivos.s3.secret-access-key:}") String secretAccessKey,
            @Value("${archivos.s3.path-style:false}") boolean pathStyle) {
        this.bucket = bucket;
        if (bucket.isBlank() || endpoint.isBlank() || accessKeyId.isBlank() || secretAccessKey.isBlank()) {
            log.warn("Adjuntos de trabajos (videos y audios) desactivados: faltan variables del bucket (BUCKET, ENDPOINT, "
                + "ACCESS_KEY_ID o SECRET_ACCESS_KEY)");
            this.cliente = null;
            this.presigner = null;
            return;
        }
        var credenciales = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        var configS3 = S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build();
        Region reg = Region.of(region.isBlank() ? "auto" : region);
        URI uri = URI.create(endpoint);
        this.cliente = S3Client.builder()
            .endpointOverride(uri).region(reg).credentialsProvider(credenciales).serviceConfiguration(configS3)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(uri).region(reg).credentialsProvider(credenciales).serviceConfiguration(configS3)
            .build();
        log.info("Adjuntos de trabajos: bucket {} en {}", bucket, endpoint);
    }

    private void exigirConfigurado() {
        if (cliente == null) {
            throw new IllegalStateException("Los videos y audios todavía no están configurados en el servidor");
        }
    }

    @Override
    public void guardar(String clave, InputStream contenido, long tamanioBytes, String tipoContenido) {
        exigirConfigurado();
        cliente.putObject(PutObjectRequest.builder().bucket(bucket).key(clave).contentType(tipoContenido)
                .contentLength(tamanioBytes).build(),
            RequestBody.fromInputStream(contenido, tamanioBytes));
    }

    @Override
    public String urlTemporal(String clave, Duration vigencia) {
        exigirConfigurado();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(vigencia)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(clave).build())
                .build())
            .url().toString();
    }

    @Override
    public void borrar(String clave) {
        exigirConfigurado();
        cliente.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
    }

    @Override
    public void destroy() {
        if (cliente != null) {
            cliente.close();
            presigner.close();
        }
    }
}
