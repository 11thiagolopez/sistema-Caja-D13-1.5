package com.thiago.escenasFX.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Foto de un trabajo a domicilio: la sube el jefe (ej. las que el cliente le mandó por WhatsApp,
 * para que el técnico vaya sabiendo con qué se encuentra) o el técnico (cómo quedó el trabajo).
 * Se guarda en la base (bytea) en vez de un storage externo para no sumar otra credencial al
 * deploy; el frontend la comprime antes de subirla (ver FotoTrabajoService para los límites).
 * Los listados nunca traen {@code contenido}: van por una proyección (FotoTrabajoRepository).
 */
@Entity
@Table(name = "fotos_trabajo")
@Getter
@Setter
public class FotoTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_foto")
    private Integer idFoto;

    @ManyToOne
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    // bytea explícito: con un length grande Hibernate elige BLOB (H2 en modo PostgreSQL no lo
    // acepta, y en Postgres sería un oid); sin length, H2 la crearía como VARBINARY(255).
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "contenido", nullable = false, columnDefinition = "bytea")
    private byte[] contenido;

    @Column(name = "tipo_contenido", nullable = false, length = 30)
    private String tipoContenido;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();
}
