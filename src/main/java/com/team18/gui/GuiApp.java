package com.team18.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GuiApp extends Application {
	@Override
	public void start(Stage stage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI.fxml"));
		Parent root = (Parent) loader.load();

		// GuiController controller = loader.getController();

		stage.setTitle("Stockholm Public Transport Router");
		Scene scene = new Scene(root, 1024, 768);
		scene.getStylesheets().add(
				getClass().getResource("/styles.css").toExternalForm());
		stage.setScene(scene);
		stage.show();
		stage.setFullScreen(false);
		stage.setMaximized(true);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
