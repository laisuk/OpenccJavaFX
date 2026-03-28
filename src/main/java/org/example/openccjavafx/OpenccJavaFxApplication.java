package org.example.openccjavafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class OpenccJavaFxApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(OpenccJavaFxApplication.class.getResource("openccjavafx-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 750);
        // Load the icon image from the resources folder (e.g., src/main/resources/images/icon.png)
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png")));
        // Add the icon to the primary stage
        stage.getIcons().add(icon);
        stage.setTitle("OpenccJavaFX");
        stage.setScene(scene);
        stage.show();
    }


    public static void main(String[] args) {
        launch();
    }
}