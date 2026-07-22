package model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;

    private String tipo; // "RETIRO"

    @Column(name = "medio_pago")
    private String medioPago;

    private double monto;
    private String motivo;
}