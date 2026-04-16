package controllers;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Venta;
import persistence.ConexionDB;

public class Controllerventas {
	@FXML
	private TableView<Venta> tablaVentas;
	@FXML
	private TableColumn<Venta, String> colDescripcion, colPago;
	@FXML
	private TableColumn<Venta, Integer> colCantidad;
	@FXML
	private TableColumn<Venta, Double> colPrecio;
	@FXML
	private Label lblEfectivo;

	@FXML
	private Label lblTarjeta;

	@FXML
	private Label lblTotalDia;

	@FXML
	private Label lblTotalRetiro;

	@FXML
	private Label lblTransferencia;
	
	public void cargarVentas() {
	    // 1. Vincular columnas con atributos de la clase Venta
		colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
	    colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
	    colPrecio.setCellValueFactory(new PropertyValueFactory<>("precio"));
	    colPago.setCellValueFactory(new PropertyValueFactory<>("medioPago"));

	    // 2. Traer los datos usando tu método estático
	    List<Venta> ventas = ConexionDB.obtenerVentasDelDia(); 
	    
	    // 3. Convertir a ObservableList y mostrar
	    ObservableList<Venta> observableList = FXCollections.observableArrayList(ventas);
	    tablaVentas.setItems(observableList);
	}
}
