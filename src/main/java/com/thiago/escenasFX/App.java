package com.thiago.escenasFX;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import persistence.ConexionDB;

import java.io.IOException;

public class App extends Application {

	// 1. Guardamos el stage de forma estática para que sea accesible desde todo el
	// sistema
	private static Stage stage;

	
	@Override
	public void start(Stage primaryStage) throws IOException {
	    stage = primaryStage;
	    stage.setTitle("Sistema D13 - Gestión de Caja");
	    
	    persistence.ConexionDB.crearTablas();

	    // 1. Verificamos si ya existe una sesión abierta
	    Double montoExistente = persistence.ConexionDB.obtenerMontoInicialHoy();

	    if (montoExistente != null) {
	        // ✅ YA HAY CAJA: Vamos directo a las ventas
	        System.out.println("🚀 Sesión activa detectada. Cargando ventas...");
	        setRoot(utils.Paths.SCENA1); 
	    } else {
	        // ❌ NO HAY CAJA: Pedimos el monto inicial
	        setRoot(utils.Paths.SCENA2); 
	    }
	    
	    stage.show();
	}

	public static void setRoot(String fxmlPath) throws IOException {
		// Usamos directamente fxmlPath porque ya viene completo desde la clase Paths
		FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxmlPath));
		Parent root = fxmlLoader.load();

		if (stage.getScene() == null) {
			stage.setScene(new Scene(root));
		} else {
			stage.getScene().setRoot(root);
		}
	}

	public static void main(String[] args) {
		launch();
	}
}