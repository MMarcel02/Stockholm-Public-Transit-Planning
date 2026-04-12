package com.team18.gui;

import com.team18.gui.Map;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.Group;
import javafx.stage.Stage;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Background;
import javafx.scene.image.Image;

public class GuiApp extends Application
{
	Map map;

	public static void main(String[] args)
	{
		launch(args);
	}

	@Override
	public void start(Stage stage)
	{
		stage.setTitle("APTBCS: Advanced Public Transit Budget Cutting Software");

		map = new Map();

		Group root = new Group(map.getNode());

		Scene scene = new Scene(root, 1200, 700);
		scene.setOnKeyPressed(ev -> map.handleKey(ev));

		stage.setScene(scene);
		stage.show();
	}
}
