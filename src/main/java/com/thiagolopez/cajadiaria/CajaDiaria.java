package com.thiagolopez.cajadiaria;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CajaDiaria implements Serializable {
    private static final long serialVersionUID = 2L; // Cambiamos la versión

    private double comienzoCaja;
    // ¡EL GRAN CAMBIO! Ahora solo guardamos una lista de transacciones.
    private List<Transaccion> transacciones;

    public CajaDiaria(double montoInicial) {
        this.comienzoCaja = montoInicial;
        this.transacciones = new ArrayList<>(); // Inicializamos la lista vacía
    }

    // --- MÉTODOS DE OPERACIÓN (MODIFICADOS) ---

    public void registrarVenta(double precio, int medioDePagoInt, String nombreProducto) {
        Transaccion.MedioDePago medio = convertirMedioDePago(medioDePagoInt);
        // En lugar de sumar a un total, creamos y añadimos un objeto Transaccion.
        this.transacciones.add(new Transaccion(medio, precio, nombreProducto));
    }

    public boolean realizarRetiroEfectivo(double monto) {
        double efectivoDisponible = getComienzoCaja() + getVentasEfectivo() - getRetirosEfectivo();
        if (monto > 0 && monto <= efectivoDisponible) {
            this.transacciones.add(new Transaccion(Transaccion.Tipo.RETIRO, Transaccion.MedioDePago.EFECTIVO, monto));
            return true;
        }
        return false;
    }

    public boolean realizarRetiroTransferencia(double monto) {
        double transferenciaDisponible = getVentasTransferencia() - getRetirosTransferencia();
        if (monto > 0 && monto <= transferenciaDisponible) {
            this.transacciones.add(new Transaccion(Transaccion.Tipo.RETIRO, Transaccion.MedioDePago.TRANSFERENCIA, monto));
            return true;
        }
        return false;
    }

    // --- MÉTODOS GETTER (AHORA CALCULAN LOS TOTALES AL MOMENTO) ---
    
    public double getComienzoCaja() { return comienzoCaja; }

    // Este método filtra la lista y suma solo las ventas en efectivo.
    public double getVentasEfectivo() {
        return transacciones.stream()
                .filter(t -> t.getTipo() == Transaccion.Tipo.VENTA && t.getMedioDePago() == Transaccion.MedioDePago.EFECTIVO)
                .mapToDouble(Transaccion::getMonto)
                .sum();
    }
    
    // Y así para cada total que necesitemos...
    public double getVentasTransferencia() {
        return transacciones.stream()
                .filter(t -> t.getTipo() == Transaccion.Tipo.VENTA && t.getMedioDePago() == Transaccion.MedioDePago.TRANSFERENCIA)
                .mapToDouble(Transaccion::getMonto)
                .sum();
    }

    public double getVentasTarjeta() {
        return transacciones.stream()
                .filter(t -> t.getTipo() == Transaccion.Tipo.VENTA && t.getMedioDePago() == Transaccion.MedioDePago.TARJETA)
                .mapToDouble(Transaccion::getMonto)
                .sum();
    }

    public double getRetirosEfectivo() {
        return transacciones.stream()
                .filter(t -> t.getTipo() == Transaccion.Tipo.RETIRO && t.getMedioDePago() == Transaccion.MedioDePago.EFECTIVO)
                .mapToDouble(Transaccion::getMonto)
                .sum();
    }

    public double getRetirosTransferencia() {
        return transacciones.stream()
                .filter(t -> t.getTipo() == Transaccion.Tipo.RETIRO && t.getMedioDePago() == Transaccion.MedioDePago.TRANSFERENCIA)
                .mapToDouble(Transaccion::getMonto)
                .sum();
    }

    // Genera la lista de Strings para los reportes a partir de la lista de transacciones.
    public List<String> getDetalleVentas() {
        return transacciones.stream()
                .filter(t -> t.getTipo() == Transaccion.Tipo.VENTA)
                .map(t -> "Producto: " + t.getNombreProducto() + " - Precio: $" + t.getMonto() + " - Medio: " + t.getMedioDePago())
                .collect(Collectors.toList());
    }

    public double getCajaFinal() {
        return getComienzoCaja() + getVentasEfectivo() - getRetirosEfectivo();
    }

    // Pequeño método ayudante para convertir el número del menú en nuestro Enum.
    private Transaccion.MedioDePago convertirMedioDePago(int medioDePagoInt) {
        switch (medioDePagoInt) {
            case 1: return Transaccion.MedioDePago.EFECTIVO;
            case 2: return Transaccion.MedioDePago.TRANSFERENCIA;
            case 3: return Transaccion.MedioDePago.TARJETA;
            default: throw new IllegalArgumentException("Medio de pago inválido: " + medioDePagoInt);
        }
    }
}