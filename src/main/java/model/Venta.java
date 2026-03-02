package model;

public class Venta {

	private String descripcion;

	private int cantidad;

	private double precio;
	
	private String MedioPago;

	public Venta(String descripcion, int cantidad, double precio, String medioPago) {
		this.descripcion = descripcion;
		this.cantidad = cantidad;
		this.precio = precio;
		this.MedioPago = medioPago;
	}

	public String getMedioPago() {
		return MedioPago;
	}

	public void setMedioPago(String medioPago) {
		MedioPago = medioPago;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public int getCantidad() {
		return cantidad;
	}

	public void setCantidad(int cantidad) {
		this.cantidad = cantidad;
	}

	public double getPrecio() {
		return precio;
	}

	public void setPrecio(double precio) {
		this.precio = precio;
	}

}
