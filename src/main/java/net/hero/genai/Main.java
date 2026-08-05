package net.hero.genai;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    @Override
    public void start(Stage primaryStage) {
        LOGGER.log(Level.INFO, "Starting IDE-style GenAI Desktop Application...");
        try {
            URL fxmlUrl = getClass().getResource("/net/hero/genai/fxml/MainWorkspace.fxml");
            if (fxmlUrl == null) {
                LOGGER.log(Level.SEVERE, "Could not find MainWorkspace.fxml");
                throw new IOException("MainWorkspace.fxml not found");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Scene scene = new Scene(root, 1200, 800);

            URL cssUrl = getClass().getResource("/net/hero/genai/css/style.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                LOGGER.log(Level.WARNING, "Could not find style.css");
            }

            primaryStage.setTitle("IDE-style GenAI Workspace");
            primaryStage.setScene(scene);
            primaryStage.show();
            LOGGER.log(Level.INFO, "Application main window shown successfully.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load the primary screen layout.", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
