package controllers;

import java.io.IOException;

import com.thiago.escenasFX.App;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;

public class ControllerLogin {
	@FXML
	private PasswordField txtPassword;

	@FXML
	private TextField txtUsuario;

	@FXML
	private Button btnLogin;

	@FXML
	private void eventAction(ActionEvent event) {

		Object evt = event.getSource();

		if (evt.equals(btnLogin)) {

			if (!txtUsuario.getText().isEmpty() && !txtPassword.getText().isEmpty()) {

				String user = txtUsuario.getText();
				String password = txtPassword.getText();
				
				try {
		            // AQUÍ movemos la lógica de decisión que estaba en App.java
		            Double montoExistente = persistence.ConexionDB.obtenerMontoInicialHoy();

		            if (montoExistente != null && montoExistente > 0) {
		                System.out.println("🚀 Sesión activa. Cargando ventas...");
		                App.setRoot(utils.Paths.SCENA1); // Ventas
		            } else {
		                System.out.println("🆕 Sin sesión hoy. Pidiendo monto inicial...");
		                App.setRoot(utils.Paths.SCENA2); // Monto Inicial
		            }
		        } catch (IOException e) {
		            e.printStackTrace();
		        }

			} else {
				// Creamos la alerta de tipo ADVERTENCIA
				Alert alert = new Alert(Alert.AlertType.WARNING);
				alert.setTitle("Campos incompletos");
				alert.setHeaderText(null); // Para que sea más limpio
				alert.setContentText("Porfavor complete los campos solicitados correctamente");

				alert.showAndWait();
			}

		}
	}

	@FXML
	void eventKey(KeyEvent event) {
		Object evt = event.getSource();

		if (evt.equals(txtUsuario)) {
			if (event.getCharacter().equals(" ")) {
				event.consume();
			}
			
			
		}else if (evt.equals(txtPassword)) {
			
			if (event.getCharacter().equals(" ")) {
				event.consume();
			}
		}
		
		
	}
	

}
