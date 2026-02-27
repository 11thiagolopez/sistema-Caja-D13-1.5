package controllers;

import com.thiago.escenasFX.App;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import utils.Paths;

public class Controller2 {

	
	   @FXML
	    void cambiarEscena(ActionEvent event) {
			App.app.setScene(Paths.SCENA1);

	    }
}
