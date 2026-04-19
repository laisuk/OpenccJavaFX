package org.example.openccjavafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.example.openccjavafx.i18n.I18n;
import org.example.openccjavafx.theme.ThemeManager;
import org.example.openccjavafx.ui.icon.AppIconFont;

import java.io.IOException;
import java.util.Objects;

public class OpenccJavaFxApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
//        AppIconFont.load();
        FXMLLoader loader = new FXMLLoader(
                OpenccJavaFxApplication.class.getResource("openccjavafx-view.fxml")
        );

        Parent root = loader.load();
        Scene scene = new Scene(root, 1000, 750);

        scene.getStylesheets().add(
                Objects.requireNonNull(
                        OpenccJavaFxApplication.class.getResource("styles.css")
                ).toExternalForm()
        );

        stage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png"))
        ));
        stage.setTitle(I18n.get("app.title"));

        ThemeManager.applySavedOrSystemTheme(root);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
