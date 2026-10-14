package org.example.openccjavafx;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import openccjava.*;
import org.example.openccjavafx.config.AppPreferences;
import org.example.openccjavafx.i18n.I18n;
import org.example.openccjavafx.theme.ThemeManager;
import org.example.openccjavafx.ui.icon.AppIconGlyph;
import org.example.openccjavafx.ui.icon.AppIconFont;
import org.example.openccjavafx.ui.icon.SymbolIcon;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.Callable;

/** FXML owns the static page; only custom dictionary rows are constructed here. */
public final class DictionaryController {
    @FXML private ScrollPane root;
    @FXML private VBox rows;
    @FXML private TextField baseDirectory, outputDirectory;
    @FXML private CheckBox startup;
    @FXML private Label activeLabel, status;
    @FXML private Label baseBrowseIcon, outputBrowseIcon, addIcon, applyIcon, generateIcon;
    private final List<Row> customRows = new ArrayList<>();
    private final BooleanProperty dark = new SimpleBooleanProperty();
    private DictionaryService service;
    private Consumer<OpenCC> activate;
    private String statusKey = "dict.ready";
    private Object[] statusArgs = {};
    private boolean restoring;
    private boolean busy;

    @FXML private void initialize() {
        // Standard Label graphics keep this FXML editable without loading application classes in Scene Builder.
        configureIcon(baseBrowseIcon, AppIconGlyph.FOLDER_OPEN);
        configureIcon(outputBrowseIcon, AppIconGlyph.FOLDER_OPEN);
        configureIcon(addIcon, AppIconGlyph.ADD_TO);
        configureIcon(applyIcon, AppIconGlyph.READING_MODE);
        configureIcon(generateIcon, AppIconGlyph.DICTIONARY);
        baseDirectory.setText(DictionaryService.appDirectory().resolve("dicts").toString());
        outputDirectory.setText(DictionaryService.appDirectory().toString());
        restoring = true;
        for (AppPreferences.DictionaryRow saved : AppPreferences.loadDictionaryRows()) {
            DictSlot slot = DictSlot.tryParse(saved.slot());
            try {
                if (slot != null) createRow(slot, CustomDictMode.valueOf(saved.mode()), saved.path());
            } catch (IllegalArgumentException ignored) { /* Ignore retired or invalid settings. */ }
        }
        restoring = false;
        startup.setSelected(AppPreferences.getApplyDictionariesAtStartup());
        startup.selectedProperty().addListener((obs, old, value) -> AppPreferences.saveApplyDictionariesAtStartup(value));
        refreshTexts();
        refreshTheme();
    }

    private void configureIcon(Label label, AppIconGlyph glyph) {
        label.setText(glyph.glyph());
        label.setFont(AppIconFont.font(16));
    }

    public void connect(DictionaryService service, Consumer<OpenCC> activate) {
        this.service = service;
        this.activate = activate;
        refreshTexts();
        if (startup.isSelected()) Platform.runLater(this::apply);
    }

    public void refreshTheme() {
        dark.set(root.getScene() == null ? ThemeManager.isEffectiveDarkMode()
                : root.getScene().getRoot().getStyleClass().contains("dark"));
    }

    public void refreshTexts() {
        localize(root);
        for (Row row : customRows) row.refreshTexts();
        refreshActiveLabel();
        status.setText(I18n.format(statusKey, statusArgs));
        baseDirectory.setTooltip(new Tooltip(I18n.get("dict.base.hint")));
        outputDirectory.setTooltip(new Tooltip(I18n.get("dict.output.hint")));
    }

    private void refreshActiveLabel() {
        activeLabel.setText(I18n.get(service != null && service.isCustom() ? "dict.active.custom" : "dict.active.default"));
    }

    private void localize(Node node) {
        if (node instanceof Labeled label && node.getUserData() instanceof String key && key.startsWith("dict."))
            label.setText(I18n.get(key));
        if (node instanceof ScrollPane scroll) localize(scroll.getContent());
        else if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(this::localize);
    }

    @FXML private void addRow() { createRow(DictSlot.STCharacters, CustomDictMode.Append, ""); persist(); }
    private void createRow(DictSlot slot, CustomDictMode mode, String path) {
        Row row = new Row(slot, mode, path);
        customRows.add(row);
        rows.getChildren().add(row.box);
    }
    private void persist() {
        if (!restoring) AppPreferences.saveDictionaryRows(customRows.stream().map(row ->
                new AppPreferences.DictionaryRow(row.slot.getValue().toCanonicalName(), row.mode.getValue().name(), row.path.getText())).toList());
    }

    private List<CustomDictSpec> specs() {
        List<CustomDictSpec> result = new ArrayList<>();
        for (int i = 0; i < customRows.size(); i++) {
            Row row = customRows.get(i);
            if (row.path.getText().isBlank()) throw new IllegalArgumentException(I18n.format("dict.row.required", i + 1));
            Path path = DictionaryService.resolve(row.path.getText());
            if (!Files.isRegularFile(path) || !Files.isReadable(path))
                throw new IllegalArgumentException(I18n.format("dict.row.missing", i + 1, path));
            result.add(CustomDictSpec.fromFile(row.slot.getValue(), path, row.mode.getValue()));
        }
        return List.copyOf(result);
    }

    @FXML private void apply() {
        if (busy || service == null) return;
        try {
            List<CustomDictSpec> specs = specs();
            run(() -> service.prepare(specs), dictionary -> {
                service.activate(dictionary, !specs.isEmpty());
                activate.accept(service.converter());
                refreshActiveLabel();
                showStatus(specs.isEmpty() ? "dict.reset" : "dict.applied");
            });
        } catch (Exception e) { failure(e); }
    }

    @FXML private void generate() {
        if (busy) return;
        try {
            Path base = directory(baseDirectory.getText(), "dict.base");
            Path output = directory(outputDirectory.getText(), "dict.output");
            List<CustomDictSpec> specs = specs();
            run(() -> DictionaryService.generate(base, output, specs), path -> showStatus("dict.generated", path));
        } catch (Exception e) { failure(e); }
    }
    private Path directory(String text, String label) {
        if (text.isBlank()) throw new IllegalArgumentException(I18n.format("dict.directory.required", I18n.get(label)));
        Path path = DictionaryService.resolve(text);
        if (!Files.isDirectory(path)) throw new IllegalArgumentException(I18n.format("dict.directory.missing", path));
        return path;
    }

    private <T> void run(Callable<T> work, Consumer<T> success) {
        // Work uses captured inputs. Prevent overlapping operations without dimming the page.
        busy = true;
        showStatus("dict.working");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(event -> {
            try { success.accept(task.getValue()); }
            catch (Exception error) { failure(error); }
            finally { busy = false; }
        });
        task.setOnFailed(event -> { busy = false; failure(task.getException()); });
        Thread thread = new Thread(task, "dictionary-worker");
        thread.setDaemon(true);
        thread.start();
    }
    private void failure(Throwable error) { showStatus("dict.failed", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()); }
    private void showStatus(String key, Object... args) { statusKey = key; statusArgs = args; status.setText(I18n.format(key, args)); }

    @FXML private void browseBase() { browseDirectory(baseDirectory, "dict.base"); }
    @FXML private void browseOutput() { browseDirectory(outputDirectory, "dict.output"); }
    private void browseDirectory(TextField field, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.get(title));
        try { Path path = DictionaryService.resolve(field.getText()); if (Files.isDirectory(path)) chooser.setInitialDirectory(path.toFile()); }
        catch (InvalidPathException ignored) { }
        File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected != null) field.setText(selected.getAbsolutePath());
    }

    private final class Row {
        final HBox box = new HBox(8);
        final ComboBox<DictSlot> slot = new ComboBox<>();
        final ComboBox<CustomDictMode> mode = new ComboBox<>();
        final TextField path = new TextField();
        final Button browse = new Button(), remove = new Button();
        Row(DictSlot selectedSlot, CustomDictMode selectedMode, String selectedPath) {
            box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            slot.getItems().setAll(DictSlot.activeSlots()); slot.setValue(selectedSlot);
            mode.getItems().setAll(CustomDictMode.values()); mode.setValue(selectedMode);
            slot.setMinWidth(190); slot.setPrefWidth(190); mode.setMinWidth(125); mode.setPrefWidth(125);
            path.setText(selectedPath); path.setMinWidth(60); HBox.setHgrow(path, Priority.ALWAYS);
            browse.setGraphic(new SymbolIcon(AppIconGlyph.DICTIONARY_ADD));
            remove.setGraphic(new SymbolIcon(AppIconGlyph.REMOVE_FROM));
            remove.getStyleClass().add("danger");
            box.getChildren().addAll(slot, mode, path, browse, remove);
            styleCombo(slot); styleCombo(mode);
            slot.valueProperty().addListener((obs, old, value) -> persist());
            mode.valueProperty().addListener((obs, old, value) -> persist());
            path.textProperty().addListener((obs, old, value) -> persist());
            remove.setOnAction(event -> { customRows.remove(this); rows.getChildren().remove(box); persist(); });
            browse.setOnAction(event -> {
                FileChooser chooser = new FileChooser(); chooser.setTitle(I18n.get("dict.file"));
                chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.get("dict.textFiles"), "*.txt"),
                        new FileChooser.ExtensionFilter(I18n.get("dict.allFiles"), "*.*"));
                try { Path parent = DictionaryService.resolve(path.getText()).getParent(); if (parent != null && Files.isDirectory(parent)) chooser.setInitialDirectory(parent.toFile()); }
                catch (InvalidPathException ignored) { }
                File selected = chooser.showOpenDialog(root.getScene().getWindow());
                if (selected != null) path.setText(selected.getAbsolutePath());
            });
            refreshTexts();
        }
        void refreshTexts() {
            browse.setText(I18n.get("dict.browse")); remove.setText(I18n.get("dict.remove"));
            path.setPromptText(I18n.get("dict.file"));
            slot.setTooltip(new Tooltip(I18n.get("dict.slot.hint")));
            mode.setTooltip(new Tooltip(I18n.get("dict.mode.hint")));
            path.setTooltip(new Tooltip(I18n.get("dict.file.hint")));
            mode.setCellFactory(view -> cell(true)); mode.setButtonCell(cell(false));
        }
    }
    private <T> void styleCombo(ComboBox<T> combo) {
        combo.styleProperty().bind(Bindings.when(dark).then("-fx-control-inner-background: #242424; -fx-text-background-color: #f3f4f6;")
                .otherwise("-fx-control-inner-background: #ffffff; -fx-text-background-color: #1f1f1f;"));
        combo.setCellFactory(view -> cell(true)); combo.setButtonCell(cell(false));
        combo.setOnShown(event -> {
            Node popup = ((ComboBoxListViewSkin<?>) combo.getSkin()).getPopupContent();
            if (!popup.getStyleClass().contains("dictionary-popup")) popup.getStyleClass().add("dictionary-popup");
            popup.styleProperty().bind(Bindings.when(dark)
                    .then("-fx-background-color: #242424; -fx-control-inner-background: #242424; -fx-border-color: #484848; -dictionary-scroll-thumb: #606060;")
                    .otherwise("-fx-background-color: white; -fx-control-inner-background: white; -fx-border-color: #dce1e7; -dictionary-scroll-thumb: #c1c7ce;"));
        });
    }
    private <T> ListCell<T> cell(boolean popup) {
        return new ListCell<>() {
            { styleProperty().bind(Bindings.createStringBinding(() ->
                    "-fx-text-fill: " + (popup && isSelected() ? "white" : dark.get() ? "#f3f4f6" : "#1f1f1f") +
                    "; -fx-background-color: " + (!popup ? "transparent" : isSelected() ? "#0078d4" : isHover() ? dark.get() ? "#383838" : "#e9f2fb" : dark.get() ? "#242424" : "white") + ";",
                    dark, selectedProperty(), hoverProperty())); }
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item instanceof CustomDictMode m ? I18n.get("dict.mode." + m.name()) : item.toString());
            }
        };
    }
}
