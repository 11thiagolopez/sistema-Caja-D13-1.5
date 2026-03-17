package controllers;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.CajaDiaria;
import model.Venta;
import persistence.ConexionDB;

public class Controller1 {

	private CajaDiaria caja;

	@FXML
	private TableView<Venta> tablaVentas;
	@FXML
	private TableColumn<Venta, String> colDescripcion, colPago;
	@FXML
	private TableColumn<Venta, Integer> colCantidad;
	@FXML
	private TableColumn<Venta, Double> colPrecio;

	@FXML
	private ComboBox<String> cbMedioPago;
	@FXML
	private TextField txtCantidad, txtDescripcion, txtPrecio;
	@FXML
	private Label lblTotal;

	@FXML
	public void initialize() {
		// 1. Preparamos la Base de Datos
		ConexionDB.crearTablas();

		// 2. Buscamos el monto inicial de hoy en SQLite
		double montoDeHoy = ConexionDB.obtenerMontoInicialHoy();
		this.caja = new CajaDiaria(montoDeHoy);

		// 3. Configuramos la interfaz
		cbMedioPago.getItems().addAll("Efectivo", "Transferencia", "Tarjeta");
		cbMedioPago.getSelectionModel().select("Efectivo");

		colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
		colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
		colPrecio.setCellValueFactory(new PropertyValueFactory<>("precio"));
		colPago.setCellValueFactory(new PropertyValueFactory<>("medioPago"));
	}

	@FXML
	private void cargarProducto() {
		try {
			String desc = txtDescripcion.getText();
			int cant = Integer.parseInt(txtCantidad.getText());
			double prec = Double.parseDouble(txtPrecio.getText());
			String medio = cbMedioPago.getValue();

			// Agregamos a la tabla visual (todavía no a la DB)
			Venta v = new Venta(desc, cant, prec, medio);
			tablaVentas.getItems().add(0, v);

			actualizarTotalUI();
			limpiarCampos();
		} catch (NumberFormatException e) {
			System.out.println("⚠️ Error: Verifique que los números sean válidos.");
		}
	}

	@FXML
	private void confirmarVenta() {
		if (tablaVentas.getItems().isEmpty())
			return;

		for (Venta v : tablaVentas.getItems()) {
			// 1. Guardamos en SQLite de forma permanente
			ConexionDB.insertarVenta(v.getDescripcion(), v.getCantidad(), v.getPrecio(), v.getMedioPago(), "VENTA");

			// 2. Registramos en la lógica de CajaDiaria para el conteo actual
			caja.registrarVenta(v.getPrecio() * v.getCantidad(), convertirMedioAInt(v.getMedioPago()),
					v.getDescripcion());
		}

		resetearInterfaz();
		System.out.println("🎯 Sincronización completa con SQLite.");
	}

	@FXML
	private void abrirVentanaRetiro() {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Retiro de Dinero - Sistema D13");
		dialog.setHeaderText("Complete los datos del retiro");

		TextField txtMonto = new TextField();
		txtMonto.setPromptText("Monto ($)");
		TextField txtMotivo = new TextField();
		txtMotivo.setPromptText("Descripción (ej: Pago Flete)");
		ComboBox<String> cbTipoRetiro = new ComboBox<>();
		cbTipoRetiro.getItems().addAll("Efectivo", "Transferencia");
		cbTipoRetiro.getSelectionModel().select(0);

		VBox layout = new VBox(10, new Label("Monto:"), txtMonto, new Label("Motivo:"), txtMotivo, new Label("Medio:"),
				cbTipoRetiro);
		layout.setPadding(new Insets(20));
		dialog.getDialogPane().setContent(layout);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		dialog.showAndWait().ifPresent(response -> {
			if (response == ButtonType.OK) {
				try {
					double monto = Double.parseDouble(txtMonto.getText());
					String motivo = txtMotivo.getText();
					String tipo = cbTipoRetiro.getValue();

					boolean exito = tipo.equals("Efectivo") ? caja.realizarRetiroEfectivo(monto, motivo)
							: caja.realizarRetiroTransferencia(monto, motivo);

					if (exito) {
						ConexionDB.insertarRetiro(motivo, monto, tipo);
						System.out.println("✅ Retiro guardado en DB: " + motivo);
					} else {
						System.out.println("❌ Fondos insuficientes en " + tipo);
					}
				} catch (NumberFormatException e) {
					System.out.println("❌ Error: Monto inválido.");
				}
			}
		});
	}

	@FXML
	public void exportarTXT() {

		SimpleDateFormat dateFormatFile = new SimpleDateFormat("dd_MM_yyyy_HHmm");
		SimpleDateFormat dateFormatHeader = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));

		// 1. Creamos la carpeta 'Historial' si no existe
		java.io.File carpetaHistorial = new java.io.File("C:/CajaCompartida/Historial");
		if (!carpetaHistorial.exists())
			carpetaHistorial.mkdirs();

		// 2. Guardamos el reporte adentro de esa carpeta
		String nombreArchivo = "C:/CajaCompartida/Historial/Reporte_" + dateFormatFile.format(new Date()) + ".txt";

		List<Venta> movimientos = ConexionDB.obtenerVentasDelDia();
		double montoInicial = ConexionDB.obtenerMontoInicialHoy();

		try (PrintWriter writer = new PrintWriter(new FileWriter(nombreArchivo))) {
			writer.println("==========================================================");
			writer.println("                SISTEMA D13 - REPORTE COMPLETO            ");
			writer.println("   Fecha: " + dateFormatHeader.format(new Date()));
			writer.println("==========================================================");
			writer.printf("💰 Monto Inicial en Caja: $ %.2f\n", montoInicial);
			writer.println("----------------------------------------------------------");
			writer.printf("%-20s %-5s %-12s %-12s\n", "DESCRIPCIÓN", "CANT", "PRECIO", "PAGO");
			writer.println("----------------------------------------------------------");

			double vEf = 0, vTr = 0, vTj = 0;
			double rEf = 0, rTr = 0;

			for (Venta v : movimientos) {
				double subtotal = v.getPrecio() * v.getCantidad();
				String medio = v.getMedioPago();

				// 1. Detalle de la fila (Agregamos el medio de pago al final)
				writer.printf("%-20s x%-4d $ %-10.2f (%s)\n", v.getDescripcion(), v.getCantidad(), subtotal, medio);

				// 2. Clasificación precisa de los totales
				if (v.getDescripcion().startsWith("RETIRO")) {
					if (medio.equalsIgnoreCase("Efectivo"))
						rEf += subtotal;
					else if (medio.equalsIgnoreCase("Transferencia"))
						rTr += subtotal;
				} else {
					if (medio.equalsIgnoreCase("Efectivo"))
						vEf += subtotal;
					else if (medio.equalsIgnoreCase("Transferencia"))
						vTr += subtotal;
					else if (medio.equalsIgnoreCase("Tarjeta"))
						vTj += subtotal;
				}
			}

			writer.println("----------------------------------------------------------");
			writer.println("                RESUMEN DE MOVIMIENTOS                    ");
			writer.println("----------------------------------------------------------");
			writer.printf("💵 EFECTIVO:      (+) $ %10.2f  (-) $ %10.2f\n", vEf, rEf);
			writer.printf("📱 TRANSFERENCIA: (+) $ %10.2f  (-) $ %10.2f\n", vTr, rTr);
			writer.printf("💳 TARJETA:       (+) $ %10.2f\n", vTj);
			writer.println("----------------------------------------------------------");

			// El Arqueo que le importa al jefe
			double efectivoFinal = montoInicial + vEf - rEf;
			writer.printf("✅ TOTAL EFECTIVO FÍSICO (CAJÓN):  $ %10.2f\n", efectivoFinal);
			writer.printf("🏦 TOTAL DIGITAL (BANCO/APP):      $ %10.2f\n", (vTr - rTr + vTj));
			writer.println("==========================================================");
			writer.printf("🚀 CAJA TOTAL DEL DÍA:            $ %10.2f\n", (efectivoFinal + vTr - rTr + vTj));
			writer.println("==========================================================");

			System.out.println("✅ Reporte completo generado: " + nombreArchivo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void actualizarTotalUI() {
		double total = 0;
		for (Venta v : tablaVentas.getItems()) {
			total += v.getPrecio() * v.getCantidad();
		}
		lblTotal.setText(String.format("$ %.2f", total));
	}

	private void limpiarCampos() {
		txtDescripcion.clear();
		txtCantidad.clear();
		txtPrecio.clear();
		txtDescripcion.requestFocus();
	}

	private void resetearInterfaz() {
		tablaVentas.getItems().clear();
		lblTotal.setText("$ 0.00");
		txtDescripcion.requestFocus();
	}

	private int convertirMedioAInt(String medio) {
		return switch (medio) {
		case "Transferencia" -> 2;
		case "Tarjeta" -> 3;
		default -> 1;
		};
	}

	@FXML
	void cancelarVenta() {
		Venta sel = tablaVentas.getSelectionModel().getSelectedItem();
		if (sel != null) {
			tablaVentas.getItems().remove(sel);
			actualizarTotalUI();
		}
	}

	@FXML
	void cerraCajaDiaria(ActionEvent event) {
		// 1. Generamos el reporte en la carpeta Historial
	    exportarTXT(); 

	    // 2. Hacemos la copia de seguridad de la Base de Datos
	    ConexionDB.respaldarBaseDeDatos();

	    // 3. Cerramos la sesión en SQL
	    ConexionDB.cerrarSesionActual();

		// 3. Opcional: Podés cerrar el programa o mostrar un aviso
		System.out.println("Caja cerrada exitosamente. El sistema se cerrará.");
		System.exit(0); // Esto cierra la aplicación por completo

	}

	@FXML
	void onEnterPrecio(ActionEvent event) {
		cargarProducto();
	}
}