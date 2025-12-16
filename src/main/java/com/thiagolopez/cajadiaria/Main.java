package com.thiagolopez.cajadiaria;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        CajaDiaria caja; // Declaramos la variable aquí

        // --- INICIO DEL PROGRAMA: Cargar o crear una caja ---
        caja = GestorArchivo.cargar();

        if (caja == null) { // Si no existe un archivo guardado, creamos una nueva caja
            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║          SISTEMA DE CAJA             ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("💰 Ingrese dinero inicial en caja: $ ");
            double comienzoCaja = Double.parseDouble(input.nextLine());
            caja = new CajaDiaria(comienzoCaja);
            GestorArchivo.guardar(caja); // Guardamos el estado inicial
            System.out.println("\n" + "═".repeat(40));
        } else {
            System.out.println("✅ Se ha cargado la sesión anterior. Caja final anterior: $" + caja.getCajaFinal());
            System.out.println("═".repeat(40));
        }

        // --- BUCLE PRINCIPAL DE OPERACIONES ---
        String entrada = "";
        while (!entrada.equals("0")) {
            System.out.println("📦 Ingrese precio del producto ($) o (R) para retirar dinero (0 para salir):");
            entrada = input.nextLine().trim();

            if (entrada.equals("0")) {
                continue; // Si es 0, salta al final del bucle para cerrarlo
            }

            if (entrada.equalsIgnoreCase("R")) {
                // Lógica de Retiro
                System.out.println("¿Deseas retirar Efectivo (E) o Transferencia (T)?");
                String tipoRetiro = input.nextLine().trim();
                System.out.print("💸 Ingrese monto a retirar: ");
                double montoRetiro = Double.parseDouble(input.nextLine());

                boolean exito = false;
                if (tipoRetiro.equalsIgnoreCase("E")) {
                    exito = caja.realizarRetiroEfectivo(montoRetiro);
                } else if (tipoRetiro.equalsIgnoreCase("T")) {
                    exito = caja.realizarRetiroTransferencia(montoRetiro);
                }

                if (exito) {
                    System.out.println("✅ Retiro realizado con éxito.");
                    GestorArchivo.guardar(caja); // ¡AUTOGUARDADO!
                } else {
                    System.out.println("❌ Monto inválido o fondos insuficientes.");
                }

            } else {
                // Lógica de Venta
                try {
                    double precio = Double.parseDouble(entrada);
                    System.out.print("💳 Ingrese medio de pago 1/ef 2/trans 3/tarj: ");
                    int medioDePago = Integer.parseInt(input.nextLine());
                    System.out.print("🏷️ Ingrese nombre del producto vendido: ");
                    String nombreProducto = input.nextLine();

                    caja.registrarVenta(precio, medioDePago, nombreProducto);
                    System.out.println("✅ Venta registrada.");
                    GestorArchivo.guardar(caja); // ¡AUTOGUARDADO!

                } catch (NumberFormatException e) {
                    System.out.println("❌ Error: Debe ingresar un número válido.");
                }
            }
            System.out.println("─".repeat(40));
        } // --- FIN DEL BUCLE WHILE ---

        // --- CIERRE DE CAJA Y REPORTES ---
        System.out.println("\nCierre de caja finalizado. Generando resumen...");
        imprimirResumen(caja);

        System.out.print("\n📄 ¿Desea exportar el reporte completo? (s/n): ");
        String respuesta = input.nextLine();
        if (respuesta.equalsIgnoreCase("s")) {
            exportarReporte(caja);
        }

        input.close(); // Se cierra el Scanner al final de todo
        System.out.println("\nPrograma terminado.");
    }

    // =================================================================================
    // MÉTODOS DE REPORTE (DENTRO DE LA CLASE Main, PERO FUERA DEL MÉTODO main)
    // =================================================================================

    private static void imprimirResumen(CajaDiaria caja) {
        // ... (código para imprimir en consola) ...
        // Este método ya lo tenías bien, solo lo reubico.
        double brutoEfectivo = caja.getVentasEfectivo();
        double brutoTransferencia = caja.getVentasTransferencia();
        double brutoTarjeta = caja.getVentasTarjeta();
        double totalBruto = brutoEfectivo + brutoTransferencia + brutoTarjeta;

        double retirosEfectivo = caja.getRetirosEfectivo();
        double retirosTransferencia = caja.getRetirosTransferencia();
        double totalRetirado = retirosEfectivo + retirosTransferencia;

        double netoEfectivo = brutoEfectivo - retirosEfectivo;
        double netoTransferencia = brutoTransferencia - retirosTransferencia;
        double netoTarjeta = brutoTarjeta;
        double totalNeto = netoEfectivo + netoTransferencia + netoTarjeta;

        System.out.println("\n\n╔════════════════════════════════════════╗");
        System.out.println("║           RESUMEN DE VENTAS            ║");
        System.out.println("╚════════════════════════════════════════╝\n");
        System.out.println("  📊 RECAUDACIÓN BRUTA:");
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.printf("│ %-25s │ %15.2f │\n", "💵 Efectivo", brutoEfectivo);
        System.out.printf("│ %-25s │ %15.2f │\n", "📱 Transferencia", brutoTransferencia);
        System.out.printf("│ %-25s │ %15.2f │\n", "💳 Tarjeta", brutoTarjeta);
        System.out.printf("│ %-25s │ %15.2f │\n", "🎯 TOTAL BRUTO", totalBruto);
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("  💸 RETIROS REALIZADOS:");
        System.out.printf("│ %-25s │ %15.2f │\n", "💵 Efectivo retirado", retirosEfectivo);
        System.out.printf("│ %-25s │ %15.2f │\n", "📱 Transferencia retirada", retirosTransferencia);
        System.out.printf("│ %-25s │ %15.2f │\n", "🎯 TOTAL RETIRADO", totalRetirado);
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("  💰 FLUJO NETO:");
        System.out.printf("│ %-25s │ %15.2f │\n", "💵 Efectivo neto", netoEfectivo);
        System.out.printf("│ %-25s │ %15.2f │\n", "📱 Transferencia neta", netoTransferencia);
        System.out.printf("│ %-25s │ %15.2f │\n", "💳 Tarjeta neta", netoTarjeta);
        System.out.printf("│ %-25s │ %15.2f │\n", "🎯 TOTAL NETO", totalNeto);
        System.out.println("├──────────────────────────────────────────┤");
        System.out.printf("│ %-25s │ %15.2f │\n", "💰 Caja inicial", caja.getComienzoCaja());
        System.out.printf("│ %-25s │ %15.2f │\n", "🏦 Caja final", caja.getCajaFinal());
        System.out.println("└──────────────────────────────────────────┘");
    }

    private static void exportarReporte(CajaDiaria caja) {
        try {
            SimpleDateFormat dateFormatFile = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy - HH_mm_ss", new Locale("es", "ES"));
            String nombreArchivo = "reporte_caja_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
            PrintWriter writer = new PrintWriter(new FileWriter(nombreArchivo));

            // --- Cálculos ---
            double brutoEfectivo = caja.getVentasEfectivo();
            double brutoTransferencia = caja.getVentasTransferencia();
            double brutoTarjeta = caja.getVentasTarjeta();
            double totalBruto = brutoEfectivo + brutoTransferencia + brutoTarjeta;
            double retirosEfectivo = caja.getRetirosEfectivo();
            double retirosTransferencia = caja.getRetirosTransferencia();
            double totalRetirado = retirosEfectivo + retirosTransferencia;
            double netoEfectivo = brutoEfectivo - retirosEfectivo;
            double netoTransferencia = brutoTransferencia - retirosTransferencia;
            double netoTarjeta = brutoTarjeta;
            double totalNeto = netoEfectivo + netoTransferencia + netoTarjeta;

            // --- Escritura en el archivo ---
            writer.println("╔══════════════════════════════════════════════╗");
            writer.println("║   REPORTE DE CAJA - " + dateFormatFile.format(new Date()));
            writer.println("╚══════════════════════════════════════════════╝\n");

            writer.println("📦 DETALLE DE PRODUCTOS VENDIDOS:");
            writer.println("┌──────────────────────────────────────────────┐");
            for (String detalle : caja.getDetalleVentas()) {
                writer.println("│ " + detalle);
            }
            writer.println("└──────────────────────────────────────────────┘\n");

            writer.println("  📊 RECAUDACIÓN BRUTA:");
            writer.println("┌──────────────────────────────────────────┐");
            writer.printf("│ %-25s │ %15.2f │\n", "💵 Efectivo", brutoEfectivo);
            writer.printf("│ %-25s │ %15.2f │\n", "📱 Transferencia", brutoTransferencia);
            writer.printf("│ %-25s │ %15.2f │\n", "💳 Tarjeta", brutoTarjeta);
            writer.printf("│ %-25s │ %15.2f │\n", "🎯 TOTAL BRUTO", totalBruto);
            writer.println("├──────────────────────────────────────────┤");
            
            writer.println("  💸 RETIROS REALIZADOS:");
            writer.printf("│ %-25s │ %15.2f │\n", "💵 Efectivo retirado", retirosEfectivo);
            writer.printf("│ %-25s │ %15.2f │\n", "📱 Transferencia retirada", retirosTransferencia);
            writer.printf("│ %-25s │ %15.2f │\n", "🎯 TOTAL RETIRADO", totalRetirado);
            writer.println("├──────────────────────────────────────────┤");
            
            writer.println("  💰 FLUJO NETO:");
            writer.printf("│ %-25s │ %15.2f │\n", "💵 Efectivo neto", netoEfectivo);
            writer.printf("│ %-25s │ %15.2f │\n", "📱 Transferencia neta", netoTransferencia);
            writer.printf("│ %-25s │ %15.2f │\n", "💳 Tarjeta neta", netoTarjeta);
            writer.printf("│ %-25s │ %15.2f │\n", "🎯 TOTAL NETO", totalNeto);
            writer.println("├──────────────────────────────────────────┤");

            writer.printf("│ %-25s │ %15.2f │\n", "💰 Caja inicial", caja.getComienzoCaja());
            writer.printf("│ %-25s │ %15.2f │\n", "🏦 Caja final", caja.getCajaFinal());
            writer.println("└──────────────────────────────────────────┘");

            writer.close();
            System.out.println("✅ Reporte exportado como: " + nombreArchivo);

        } catch (IOException e) {
            System.out.println("❌ Error al crear el archivo: " + e.getMessage());
        }
    
    }
}