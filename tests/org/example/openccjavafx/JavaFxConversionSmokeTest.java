package org.example.openccjavafx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import org.example.openccjavafx.config.AppPreferences;
import org.example.openccjavafx.i18n.I18n;
import org.example.openccjavafx.theme.ThemeManager;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

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
            verifyDictionaryPage(loader, root);
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

    private void verifyDictionaryPage(FXMLLoader loader, Parent root) throws Exception {
        var savedRows = AppPreferences.loadDictionaryRows();
        var locale = I18n.getLocale();
        DictionaryController dictionary = (DictionaryController) loader.getNamespace().get("dictionaryViewController");
        Tab tab = (Tab) loader.getNamespace().get("tabDictionary");
        ((TabPane) tab.getTabPane()).getSelectionModel().select(tab);
        ScrollPane page = (ScrollPane) tab.getContent();
        Stage stage = new Stage();
        stage.setOpacity(0);
        stage.setScene(root.getScene());
        try {
            stage.show();
            root.applyCss(); root.layout();
            VBox rows = (VBox) page.lookup("#rows");
            int before = rows.getChildren().size();
            page.lookupAll(".button").stream().filter(node -> "dict.add".equals(node.getUserData()))
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            assertEquals(before + 1, rows.getChildren().size());
            Parent row = (Parent) rows.getChildren().get(before);
            var combos = row.getChildrenUnmodifiable().stream().filter(ComboBox.class::isInstance).map(ComboBox.class::cast).toList();
            ComboBox<?> slot = combos.get(0), mode = combos.get(1);
            assertEquals(openccjava.DictSlot.activeSlots().size(), slot.getItems().size());
            for (boolean dark : new boolean[]{false, true}) {
                ThemeManager.applyTheme(root, dark);
                dictionary.refreshTheme();
                for (var language : new java.util.Locale[]{java.util.Locale.ENGLISH, java.util.Locale.SIMPLIFIED_CHINESE, java.util.Locale.TRADITIONAL_CHINESE}) {
                    I18n.setLocale(language); dictionary.refreshTexts();
                    root.applyCss(); root.layout();
                    assertEquals(I18n.get("dict.mode.Append"), mode.getButtonCell().getText());
                    assertFalse(mode.getButtonCell().getText().startsWith("!"));
                }
                I18n.setLocale(java.util.Locale.ENGLISH); dictionary.refreshTexts();
                root.applyCss(); root.layout();
                saveSnapshot(root, "dictionary-" + (dark ? "dark" : "light"));
                for (ComboBox<?> combo : combos) {
                    combo.show();
                    Node popup = ((ComboBoxListViewSkin<?>) combo.getSkin()).getPopupContent();
                    popup.applyCss();
                    assertTrue(popup.getStyle().contains(dark ? "#242424" : "white"));
                    saveSnapshot(popup, "dictionary-popup-" + (combo == slot ? "slot-" : "mode-") + (dark ? "dark" : "light"));
                    combo.hide();
                }
            }
            ((Button) row.getChildrenUnmodifiable().get(4)).fire();
            assertEquals(before, rows.getChildren().size());
        } finally {
            stage.close();
            AppPreferences.saveDictionaryRows(savedRows);
            I18n.setLocale(locale);
        }
    }

    private void saveSnapshot(Node node, String name) throws Exception {
        WritableImage image = node.snapshot(null, null);
        java.awt.image.BufferedImage output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        java.nio.file.Path directory = java.nio.file.Path.of("build/test-snapshots");
        java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }
}
