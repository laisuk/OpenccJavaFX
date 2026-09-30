package org.example.openccjavafx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Requires a desktop: ./gradlew test -PguiSmokeTest=true */
@EnabledIfSystemProperty(named = "guiSmokeTest", matches = "true")
class JavaFxConversionSmokeTest {
    @Test
    void fxmlControllerConvertsBothDirectionsAndSelectedText() throws Exception {
        FutureTask<Void> smoke = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(OpenccJavaFxApplication.class.getResource("openccjavafx-view.fxml"));
            Parent root = loader.load();
            new Scene(root, 1000, 800);
            root.applyCss();
            root.layout();
            OpenccJavaFxController controller = loader.getController();
            CodeArea source = (CodeArea) loader.getNamespace().get("textAreaSource");
            CodeArea destination = (CodeArea) loader.getNamespace().get("textAreaDestination");
            ((RadioButton) loader.getNamespace().get("rbS2t")).setSelected(true);
            ((CheckBox) loader.getNamespace().get("cbPunctuation")).setSelected(true);
            source.replaceText("“汉语测试”");
            controller.onBtnStartClicked();
            assertEquals("「漢語測試」", destination.getText());

            ((RadioButton) loader.getNamespace().get("rbT2s")).setSelected(true);
            source.replaceText("「漢語測試」");
            controller.onBtnStartClicked();
            assertEquals("“汉语测试”", destination.getText());

            ((RadioButton) loader.getNamespace().get("rbS2t")).setSelected(true);
            source.replaceText("汉语测试");
            source.selectRange(0, 2);
            controller.onBtnStartClicked();
            assertEquals("漢語", destination.getText());
            return null;
        });
        Platform.startup(() -> {});
        try {
            Platform.runLater(smoke);
            smoke.get(30, TimeUnit.SECONDS);
        } finally {
            Platform.exit();
        }
    }
}
