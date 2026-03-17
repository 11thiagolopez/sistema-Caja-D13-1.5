package com.thiago.escenasFX;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import persistence.ConexionDB;

import java.io.IOException;

public class App extends Application {

    private static Stage stage;

    @Override
    public void start(Stage primaryStage) throws IOException {
        stage = primaryStage;
        stage.setTitle("Sistema D13 - Gestión de Caja");
        
        // 1. Preparamos las tablas
        persistence.ConexionDB.crearTablas();

        // 2. Verificamos si ya existe una sesión abierta hoy
        Double montoExistente = persistence.ConexionDB.obtenerMontoInicialHoy();

        if (montoExistente != null && montoExistente > 0) {
            // ✅ YA HAY CAJA: Vamos directo a las ventas
            System.out.println("🚀 Sesión activa detectada. Cargando ventas...");
            setRoot(utils.Paths.SCENA1); 
        } else {
            // ❌ NO HAY CAJA: Pedimos el monto inicial
            System.out.println("🆕 No hay sesión para hoy. Pidiendo monto inicial...");
            setRoot(utils.Paths.SCENA2); 
        }
        
        stage.show();
    }

    public static void setRoot(String fxmlPath) throws IOException {
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