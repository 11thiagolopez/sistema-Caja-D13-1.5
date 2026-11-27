package com.thiago.escenasFX.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Video o audio adjunto a un trabajo a domicilio (ej. el audio o el video que el cliente mandó por
 * WhatsApp para dar contexto). A diferencia de las fotos, el archivo NO va en la base (un video pesa
 * hasta 100 MB): vive en el Railway Storage Bucket bajo {@code claveArchivo}; acá queda solo la
 * referencia. Reglas en ArchivoTrabajoService.
 */
@Entity
@Table(name = "archivos_trabajo")
@Getter
@Setter
public class ArchivoTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo")
    private Integer idArchivo;

    @ManyToOne
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    // Ruta dentro del bucket, ej. "trabajos/123/5b1f...e2.mp4".
    @Column(name = "clave_archivo", nullable = false, length = 200)
    private String claveArchivo;

    // video/mp4, video/quicktime, audio/ogg, audio/mpeg... (detectado por los bytes, no por el navegador).
    @Column(name = "tipo_contenido", nullable = false, length = 30)
    private String tipoContenido;

    @Column(name = "tamanio_bytes", nullable = false)
    private Long tamanioBytes;

    // null = adjunto (lo que manda el cliente, o lo que suma el jefe o el técnico para dar contexto);
    // ANTES / DESPUES = lo grabó el técnico como comprobante, igual que las fotos.
    @Column(name = "momento", length = 10)
    private String momento;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();
}
