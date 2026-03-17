package controllers;

import java.io.IOException;

import com.thiago.escenasFX.App;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import model.CajaDiaria;
import persistence.ConexionDB;
import utils.Paths;

public class Controller2 {
	double montoInicial = 0;

	@FXML
	private TextField txtMonto;

	@FXML
	void confrimarMontoInicial(ActionEvent event) {
	    try {
	        String texto = txtMonto.getText();
	        if (texto.isEmpty()) return;

	        double valor = Double.parseDouble(texto);
	        montoInicial = valor;

	        // 1. PERSISTENCIA: Guardamos en SQLite para que el otro usuario lo vea
	        ConexionDB.insertarSesion(valor);

	        // 2. NAVEGACIÓN: Saltamos a la pantalla de ventas (Scene1)
	        App.setRoot(utils.Paths.SCENA1);

	    } catch (NumberFormatException e) {
	        System.out.println("⚠️ Por favor, introduce un número válido.");
	    } catch (IOException e) {
	        System.err.println("❌ Error: No se pudo cargar la ruta " + utils.Paths.SCENA1);
	        e.printStackTrace();
	}
	}
	

	public double getMontoInicial() {
		return montoInicial;
	}


	public void setMontoInicial(double montoInicial) {
		this.montoInicial = montoInicial;
	}



	
}
