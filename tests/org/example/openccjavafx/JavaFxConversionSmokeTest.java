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
            verifyDictionaryDesignLoad();
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
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            Platform.runLater(smoke);
            smoke.get(30, TimeUnit.SECONDS);
            verifyAsyncStatusDoesNotDimOrRebuildControls();
        } finally {
            Platform.exit();
        }
    }

    private void verifyAsyncStatusDoesNotDimOrRebuildControls() throws Exception {
        var savedRows = AppPreferences.loadDictionaryRows();
        var release = new java.util.concurrent.CountDownLatch(1);
        var completed = new java.util.concurrent.CompletableFuture<Void>();
        var fixture = new java.util.concurrent.atomic.AtomicReference<FXMLLoader>();
        var oldModeCell = new java.util.concurrent.atomic.AtomicReference<ListCell<?>>();
        var oldTooltip = new java.util.concurrent.atomic.AtomicReference<Tooltip>();
        FutureTask<Void> start = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(OpenccJavaFxApplication.class.getResource("dictionary-view.fxml"));
            ScrollPane page = loader.load();
            fixture.set(loader);
            new Scene(page);
            page.applyCss();
            page.layout();
            VBox controls = (VBox) loader.getNamespace().get("controls");
            page.lookupAll(".button").stream().filter(node -> "dict.add".equals(node.getUserData()))
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            VBox rows = (VBox) loader.getNamespace().get("rows");
            Parent lastRow = (Parent) rows.getChildren().get(rows.getChildren().size() - 1);
            ComboBox<?> mode = (ComboBox<?>) lastRow.getChildrenUnmodifiable().get(1);
            oldModeCell.set(mode.getButtonCell());
            TextField base = (TextField) loader.getNamespace().get("baseDirectory");
            oldTooltip.set(base.getTooltip());
            DictionaryController controller = loader.getController();
            var run = DictionaryController.class.getDeclaredMethod("run", java.util.concurrent.Callable.class, java.util.function.Consumer.class);
            run.setAccessible(true);
            run.invoke(controller, (java.util.concurrent.Callable<Object>) () -> {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for UI check");
                return null;
            }, (java.util.function.Consumer<Object>) value -> completed.complete(null));
            assertFalse(controls.isDisabled());
            assertFalse(base.isDisabled());
            // Clicking Generate while busy must be ignored, even if the newly edited input is invalid.
            base.setText("invalid\u0000path");
            page.lookupAll(".button").stream().filter(node -> "dict.generate".equals(node.getUserData()))
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            assertEquals(I18n.get("dict.working"), ((Label) loader.getNamespace().get("status")).getText());
            return null;
        });
        try {
            Platform.runLater(start);
            start.get(10, TimeUnit.SECONDS);
            release.countDown();
            completed.get(10, TimeUnit.SECONDS);
            FutureTask<Void> finish = new FutureTask<>(() -> {
                FXMLLoader loader = fixture.get();
                VBox rows = (VBox) loader.getNamespace().get("rows");
                Parent lastRow = (Parent) rows.getChildren().get(rows.getChildren().size() - 1);
                ComboBox<?> mode = (ComboBox<?>) lastRow.getChildrenUnmodifiable().get(1);
                assertSame(oldModeCell.get(), mode.getButtonCell());
                assertSame(oldTooltip.get(), ((TextField) loader.getNamespace().get("baseDirectory")).getTooltip());
                assertFalse(((VBox) loader.getNamespace().get("controls")).isDisabled());
                return null;
            });
            Platform.runLater(finish);
            finish.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            AppPreferences.saveDictionaryRows(savedRows);
        }
    }

    private void verifyDictionaryDesignLoad() throws Exception {
        var location = OpenccJavaFxApplication.class.getResource("dictionary-view.fxml");
        String fxml;
        try (var input = location.openStream()) {
            fxml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // Scene Builder ignores controllers and handlers and has no application classes on its classpath.
        fxml = fxml.replaceAll("\\s+fx:controller=\"[^\"]*\"", "")
                .replaceAll("\\s+onAction=\"[^\"]*\"", "");
        FXMLLoader designer = new FXMLLoader(location);
        designer.setClassLoader(new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.example.openccjavafx.") || name.startsWith("openccjava."))
                    throw new ClassNotFoundException("Application class unavailable to designer: " + name);
                return super.loadClass(name, resolve);
            }
        });
        try (var input = new java.io.ByteArrayInputStream(fxml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            assertInstanceOf(ScrollPane.class, designer.load(input));
        }
        assertEquals(Label.class, designer.getNamespace().get("baseBrowseIcon").getClass());
        assertEquals(org.example.openccjavafx.ui.icon.AppIconGlyph.FOLDER_OPEN.glyph(),
                ((Label) designer.getNamespace().get("baseBrowseIcon")).getText());
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
            verifyStableStatusFrame(page, root);
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

    private void verifyStableStatusFrame(ScrollPane page, Parent root) {
        Label status = (Label) page.lookup("#status");
        Node frame = page.lookup("#statusFrame");
        String original = status.getText();
        double contentHeight = page.getContent().getLayoutBounds().getHeight();
        double frameHeight = frame.getLayoutBounds().getHeight();
        try {
            status.setText("Processing dictionary…");
            root.applyCss(); root.layout();
            assertEquals(contentHeight, page.getContent().getLayoutBounds().getHeight(), 0.01);
            assertEquals(frameHeight, frame.getLayoutBounds().getHeight(), 0.01);
            status.setText("A long output path or error message: ".repeat(30));
            root.applyCss(); root.layout();
            assertEquals(contentHeight, page.getContent().getLayoutBounds().getHeight(), 0.01);
            assertEquals(frameHeight, frame.getLayoutBounds().getHeight(), 0.01);
        } finally {
            status.setText(original);
            root.applyCss(); root.layout();
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
