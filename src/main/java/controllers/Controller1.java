package controllers;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;

import persistence.GestorArchivo; // Cambiá 'persistence' por el nombre real de tu paquete
import model.CajaDiaria;
import model.Transaccion;

import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;

import com.thiago.escenasFX.App;

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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import model.CajaDiaria;
import model.Venta;
import utils.Paths;

public class Controller1 {

	private CajaDiaria caja;

	@FXML
	private TableColumn<Venta, String> colPago;

	@FXML
	private TableColumn<Venta, Integer> colCantidad;

	@FXML
	private TableColumn<Venta, String> colDescripcion;

	@FXML
	private TableColumn<Venta, Double> colPrecio;

	@FXML
	private TableView<Venta> tablaVentas;

	@FXML
	private AnchorPane paneSuperior;

	@FXML
	private ComboBox<String> cbMedioPago;

	@FXML
	private TextField txtCantidad;

	@FXML
	private TextField txtDescripcion;

	@FXML
	private TextField txtPrecio;

	@FXML
	private Label lblTotal;

	@FXML
	public void initialize() {
		caja = GestorArchivo.cargar();

		if (caja == null) {
			caja = new CajaDiaria(0); // Si no hay archivo, creamos una nueva con $0 inicial
		}
		cbMedioPago.getItems().addAll("Efectivo", "Transferencia", "Tarjeta");

		// Dejamos "Efectivo" seleccionado por defecto para ahorrar un clic al vendedor
		cbMedioPago.getSelectionModel().select("Efectivo");

		colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
		colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
		colPrecio.setCellValueFactory(new PropertyValueFactory<>("precio"));
		colPago.setCellValueFactory(new PropertyValueFactory<>("medioPago"));

	}

	private int convertirMedioAInt(String medio) {
		return switch (medio) {
		case "Efectivo" -> 1;
		case "Transferencia" -> 2;
		case "Tarjeta" -> 3;
		default -> 1;
		};
	}

	@FXML
	private void cargarProducto() {
		try {
			String desc = txtDescripcion.getText();
			int cant = Integer.parseInt(txtCantidad.getText());
			double prec = Double.parseDouble(txtPrecio.getText());

			// Capturamos el texto seleccionado (Efectivo, Transferencia, etc.)
			String medioSeleccionado = cbMedioPago.getValue();

			// Creamos la venta (Si querés ver el medio en la tabla, agregalo a tu clase
			// Venta)
			Venta v = new Venta(desc, cant, prec, medioSeleccionado);
			tablaVentas.getItems().add(0, v);

			actualizarTotal();
			limpiarCampos();
		} catch (Exception e) {
			System.out.println("Error en la carga");
		}
	}

	private void limpiarCampos() {
		txtDescripcion.clear();
		txtCantidad.clear();
		txtPrecio.clear();

		// El cursor vuelve solo a la descripción para la siguiente venta
		txtDescripcion.requestFocus();
	}

	private void actualizarTotal() {
		double total = 0;
		for (Venta v : tablaVentas.getItems()) {
			total += v.getPrecio() * v.getCantidad();
		}
		lblTotal.setText(String.format("$ %.2f", total));
	}

	@FXML
	void onEnterPrecio(ActionEvent event) {
		cargarProducto(); // Llama al mismo método del botón
	}

	@FXML
	void cancelarVenta(ActionEvent event) {

	}

	@FXML
	void confirmarVenta(ActionEvent event) {
		if (tablaVentas.getItems().isEmpty())
			return;

		// 3. Procesamos los datos de la tabla y los metemos en la lógica de CajaDiaria
		int medioInt = convertirMedioAInt(cbMedioPago.getValue());

		for (Venta v : tablaVentas.getItems()) {
			// El método registrarVenta ahora vive adentro de esta acción
			double totalProducto = v.getPrecio() * v.getCantidad();
			caja.registrarVenta(totalProducto, medioInt, v.getDescripcion());
		}

		// 4. Guardamos el archivo Serializable (Autoguardado)
		GestorArchivo.guardar(caja);

		// 5. Limpiamos la pantalla para la siguiente venta
		limpiarCampos();
		resetearInterfaz();
		System.out.println("✅ Venta confirmada y guardada en el archivo .dat");
	}

	private void resetearInterfaz() {
		tablaVentas.getItems().clear();

		// Usamos el formateador para asegurar los dos decimales (.2f)
		double totalInicial = 0.0;
		lblTotal.setText(String.format("$ %.2f", totalInicial));

		txtDescripcion.requestFocus();
	}
	@FXML
	private void abrirVentanaRetiro() {
	    // 1. Configuración del Diálogo
	    Dialog<ButtonType> dialog = new Dialog<>();
	    dialog.setTitle("Retiro de Dinero - Sistema D13");
	    dialog.setHeaderText("Complete los datos del retiro");

	    // 2. Componentes: Monto, Motivo y el ComboBox que pediste
	    TextField txtMonto = new TextField();
	    txtMonto.setPromptText("Monto ($)");
	    
	    TextField txtMotivo = new TextField();
	    txtMotivo.setPromptText("Descripción (ej: Pago Flete)");

	    ComboBox<String> cbTipoRetiro = new ComboBox<>();
	    cbTipoRetiro.getItems().addAll("Efectivo", "Transferencia");
	    cbTipoRetiro.getSelectionModel().select(0); // Por defecto Efectivo

	    // 3. Layout del Diálogo
	    VBox layout = new VBox(10, 
	        new Label("Monto:"), txtMonto, 
	        new Label("Motivo/Descripción:"), txtMotivo,
	        new Label("Medio de Retiro:"), cbTipoRetiro
	    );
	    layout.setPadding(new Insets(20));
	    dialog.getDialogPane().setContent(layout);
	    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

	    // 4. Lógica al presionar OK
	    dialog.showAndWait().ifPresent(response -> {
	        if (response == ButtonType.OK) {
	            try {
	                double monto = Double.parseDouble(txtMonto.getText());
	                String motivo = txtMotivo.getText();
	                String tipo = cbTipoRetiro.getValue();

	                boolean exito = false;
	                // Usamos la lógica que ya tenías en tu clase CajaDiaria
	                if (tipo.equals("Efectivo")) {
	                    exito = caja.realizarRetiroEfectivo(monto, motivo);
	                } else {
	                    exito = caja.realizarRetiroTransferencia(monto, motivo);
	                }

	                if (exito) {
	                    GestorArchivo.guardar(caja);
	                    System.out.println("✅ Retiro guardado: " + motivo);
	                } else {
	                    System.out.println("❌ Fondos insuficientes para el retiro.");
	                }
	            } catch (NumberFormatException e) {
	                System.out.println("❌ Error: Ingrese un monto numérico válido.");
	            }
	        }
	    });
	}
	
	@FXML
	void exportarTXT(ActionEvent event) {
	    // 1. Cargamos la caja desde el archivo serializable
	    CajaDiaria caja = GestorArchivo.cargar();
	    
	    if (caja != null) {
	        exportarReporteTXT(caja);
	        System.out.println("✅ Reporte generado con éxito.");
	        
	        // Cierre opcional: Si querés que el programa se cierre al exportar
	        // System.exit(0); 
	    } else {
	        System.out.println("❌ No hay datos de caja para exportar.");
	    }
	}

	private void exportarReporteTXT(CajaDiaria caja) {
	    try {
	        SimpleDateFormat dateFormatFile = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy - HH_mm_ss", new Locale("es", "ES"));
	        String nombreArchivo = "reporte_caja_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
	        PrintWriter writer = new PrintWriter(new FileWriter(nombreArchivo));

	        // --- ENCABEZADO ---
	        writer.println("╔══════════════════════════════════════════════╗");
	        writer.println("║   REPORTE DE CAJA - SISTEMA D13");
	        writer.println("║   Fecha: " + dateFormatFile.format(new Date()));
	        writer.println("╚══════════════════════════════════════════════╝\n");

	        // --- DETALLE DE VENTAS ---
	        writer.println("📦 DETALLE DE PRODUCTOS VENDIDOS:");
	        writer.println("┌──────────────────────────────────────────────┐");
	        for (String detalle : caja.getDetalleVentas()) {
	            writer.println("│ " + detalle);
	        }
	        writer.println("└──────────────────────────────────────────────┘\n");

	        // --- DETALLE DE RETIROS (Aquí agregamos la descripción/motivo) ---
	        writer.println("💸 DETALLE DE RETIROS Y SALIDAS:");
	        writer.println("┌──────────────────────────────────────────────┐");
	        // Nota: Asumimos que agregaste un getter para transacciones o usamos lógica interna
	        for (Transaccion t : caja.getTransacciones()) {
	            if (t.getTipo() == Transaccion.Tipo.RETIRO) {
	                // Usamos t.getNombreProducto() porque ahí guardamos el motivo
	                writer.printf("│ Motivo: %-20s | Monto: $%10.2f | Medio: %s\n", 
	                    t.getNombreProducto(), t.getMonto(), t.getMedioDePago());
	            }
	        }
	        writer.println("└──────────────────────────────────────────────┘\n");

	        // --- RESUMEN DE TOTALES ---
	        escribirSeccionTotales(writer, caja);

	        writer.close();
	        System.out.println("✅ Reporte exportado como: " + nombreArchivo);

	    } catch (IOException e) {
	        System.out.println("❌ Error al crear el archivo: " + e.getMessage());
	    }
	}

	// Método auxiliar para no repetir código de totales
	private void escribirSeccionTotales(PrintWriter writer, CajaDiaria caja) {
	    writer.println("  💰 RESUMEN FINAL:");
	    writer.println("┌──────────────────────────────────────────┐");
	    writer.printf("│ %-25s │ %15.2f │\n", "💰 Caja inicial", caja.getComienzoCaja());
	    writer.printf("│ %-25s │ %15.2f │\n", "💵 Ventas Efectivo", caja.getVentasEfectivo());
	    writer.printf("│ %-25s │ %15.2f │\n", "💸 Retiros Efectivo", caja.getRetirosEfectivo());
	    writer.println("├──────────────────────────────────────────┤");
	    writer.printf("│ %-25s │ %15.2f │\n", "🏦 CAJA FINAL (Efectivo)", caja.getCajaFinal());
	    writer.println("└──────────────────────────────────────────┘");
	}
		  
		
    }

