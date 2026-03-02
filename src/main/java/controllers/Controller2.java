package controllers;

import com.thiago.escenasFX.App;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import utils.Paths;

public class Controller2 {

	
	   @FXML
	    void cambiarEscena(ActionEvent event) {
			App.app.setScene(Paths.SCENA1);

	    }
	   
}