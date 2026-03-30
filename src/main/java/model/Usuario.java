package model;

public class Usuario {
private String nombreUsuario;
private String password;
private String nombre_completo;

// Constructor, Getters y Setters
public Usuario(String nombreUsuario, String password, String nombreCompleto) {
    this.nombreUsuario = nombreUsuario;
    this.password = password;
    this.nombre_completo = nombreCompleto;
}

public String getNombreUsuario() { return nombreUsuario; }
public String getPassword() { return password;

}

public String getNombreCompleto() {
	return nombre_completo;
}

public void setNombreCompleto(String nombreCompleto) {
	this.nombre_completo = nombreCompleto;
}

}
