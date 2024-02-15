package org.example.demofx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class DemoFxApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(DemoFxApplication.class.getResource("demofx-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 960, 680);
        stage.setTitle("DemoFX 1.0");
        stage.setScene(scene);
        stage.show();
    }


    public static void main(String[] args) {
        launch();
    }
}