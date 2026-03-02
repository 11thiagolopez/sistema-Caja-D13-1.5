package controllers;

import java.net.URL;
import persistence.GestorArchivo; // Cambiá 'persistence' por el nombre real de tu paquete
import model.CajaDiaria;
import java.util.ResourceBundle;

import com.thiago.escenasFX.App;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
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
}
