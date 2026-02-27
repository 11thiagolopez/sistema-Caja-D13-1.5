package controllers;

import java.net.URL;
import java.util.ResourceBundle;

import com.thiago.escenasFX.App;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import utils.Paths;

public class Controller1 {

	@FXML
	void cambiarEscena(ActionEvent event) {
		App.app.setScene(Paths.SCENA2);
	}
	@FXML
	private AnchorPane paneSuperior;
}
