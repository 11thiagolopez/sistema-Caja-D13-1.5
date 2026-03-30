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
            stage.setTitle("Sistema D13 - Login");
            
            // 1. Preparamos las tablas (mantenemos esto aquí)
            persistence.ConexionDB.crearTablas();

            // 2. Cargamos SIEMPRE el Login primero
            // Asegúrate de tener la ruta del login en tus utils.Paths
            setRoot(utils.Paths.SCENALOGIN); 
            
            stage.show();
        }

        // El método setRoot se mantiene igual, es muy útil para cambiar escenas
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
