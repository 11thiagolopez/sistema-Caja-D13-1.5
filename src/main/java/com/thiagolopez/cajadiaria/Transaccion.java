package com.thiagolopez.cajadiaria;
import java.io.Serializable;

public class Transaccion implements Serializable {
    private static final long serialVersionUID = 1L;

    // Usamos 'enums' para definir tipos fijos. Es más seguro que usar Strings.
    public enum Tipo { VENTA, RETIRO }
    public enum MedioDePago { EFECTIVO, TRANSFERENCIA, TARJETA }

    private final Tipo tipo;
    private final MedioDePago medioDePago;
    private final double monto;
    private final String nombreProducto; // Será null para los retiros

    // Constructor para Ventas
    public Transaccion(MedioDePago medio, double monto, String producto) {
        this.tipo = Tipo.VENTA;
        this.medioDePago = medio;
        this.monto = monto;
        this.nombreProducto = producto;
    }

    // Constructor para Retiros
    public Transaccion(Tipo tipo, MedioDePago medio, double monto) {
        if (tipo == Tipo.VENTA) {
            throw new IllegalArgumentException("Usa el otro constructor para ventas.");
        }
        this.tipo = tipo;
        this.medioDePago = medio;
        this.monto = monto;
        this.nombreProducto = null;
    }

    // Getters para acceder a los datos desde otras clases
    public Tipo getTipo() { return tipo; }
    public MedioDePago getMedioDePago() { return medioDePago; }
    public double getMonto() { return monto; }
    public String getNombreProducto() { return nombreProducto; }
}
