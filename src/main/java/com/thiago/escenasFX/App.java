package com.thiago.escenasFX;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class App extends Application {

    // 1. Guardamos el stage de forma estática para que sea accesible desde todo el sistema
    private static Stage stage;

    @Override
    public void start(Stage primaryStage) throws IOException {
        stage = primaryStage; // Guardamos la referencia
        stage.setTitle("Sistema D13 - Gestión de Caja");
        
        // Arrancamos con la pantalla de Monto Inicial (Scene2)
        setRoot(utils.Paths.SCENA2); 
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