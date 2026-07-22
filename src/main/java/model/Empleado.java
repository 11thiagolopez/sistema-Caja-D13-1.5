package model;

import jakarta.persistence.*;

@Entity
@Table(name = "empleados")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Long idEmpleado;

    private String nombre;
    private String usuario;

    @Column(name = "password_hash")
    private String passwordHash;

    private String rol;

    // getters/setters
}
