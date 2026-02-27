package com.thiago.escenasFX;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import utils.Paths;

public class App extends Application {

	public static App app;
	private Stage stageWindow;

	public static void main(String[] args) {
		launch();
	}

	public void start(Stage stage) {
		app = this;
		stageWindow = stage;
		setScene(Paths.SCENA1);

	}

	public void setScene(String path) {
		FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
		try {
			Parent root = loader.load();
			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("/com/thiago/escenasFX/styles.css").toExternalForm());
			stageWindow.setScene(scene);
			stageWindow.show();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}