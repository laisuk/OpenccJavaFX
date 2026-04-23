package org.example.openccjavafx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.ProgressBar;
import openccjava.OfficeHelper;
import openccjava.OpenccConfig;
import openxmlhelper.EpubHelper;
import openxmlhelper.OpenDocumentHelper;
import openxmlhelper.OpenXmlHelper;
import org.example.openccjavafx.config.AppPreferences;
import org.example.openccjavafx.i18n.I18n;
import org.example.openccjavafx.i18n.UiLanguage;
import org.example.openccjavafx.theme.ThemeManager;
import org.example.openccjavafx.ui.ConversionComboBoxHelper;
import org.example.openccjavafx.ui.EditorFontHelper;
import org.example.openccjavafx.ui.icon.AppIconGlyph;
import org.example.openccjavafx.ui.icon.SymbolIcon;
import org.fxmisc.richtext.CodeArea;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import openccjava.OpenCC;
import org.fxmisc.richtext.LineNumberFactory;
import pdfboxhelper.PdfBoxHelper;
import pdfboxhelper.PdfReflowHelper;
//import org.fxmisc.richtext.LineNumberFactory;

public class OpenccJavaFxController {
    private static final Set<String> FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".txt", ".xml", ".srt", ".ass", ".vtt", ".json", ".ttml2",
            ".csv", ".java", ".md", ".html", ".cs", ".py", ".cpp"
    ));
    private int currentSourceCode = 0; // 0=non-zho, 1=hant, 2=hans

    @FXML
//    private TextArea textAreaSource;
    private CodeArea textAreaSource;
    @FXML
//    private TextArea textAreaDestination;
    private CodeArea textAreaDestination;
    @FXML
//    private TextArea textAreaPreview;
    private CodeArea textAreaPreview;
    @FXML
    private RadioButton rbS2t;
    @FXML
    private RadioButton rbT2s;
    @FXML
    private RadioButton rbManual;
    @FXML
    private RadioButton rbStd;
    @FXML
    private RadioButton rbZHTW;
    @FXML
    private RadioButton rbHK;
    @FXML
    private CheckBox cbZHTW;
    @FXML
    private CheckBox cbPunctuation;
    @FXML
    private CheckBox cbConvertFilename;
    @FXML
    private CheckBox cbLineNumber;
    @FXML
    private Label lblSource;
    @FXML
    private Label lblSourceCode;
    @FXML
    private Label lblDestination;
    @FXML
    private Label lblDestinationCode;
    @FXML
    private Label lblSourceCharCount;
    @FXML
    private Label lblOutputFolder;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblFilename;
    @FXML
    private Tab tabMain;
    @FXML
    private Tab tabBatch;
    @FXML
    private Tab tabSettings;
    @FXML
    private Button btnOpenFile;
    @FXML
    private Button btnExit;
    @FXML
    private Button btnClearSource;
    @FXML
    private Button btnPaste;
    @FXML
    private Button btnClearDestination;
    @FXML
    private Button btnCopy;
    @FXML
    private ListView<String> listViewSource;
    @FXML
    private TextField textFieldPath;
    @FXML
    private ComboBox<ConfigItem> cbManual;
    @FXML
    private ComboBox<SaveTargetItem> cbSaveTarget;
    @FXML
    private ComboBox<String> cbEditorFont;
    @FXML
    private Spinner<Integer> spnFontSize;
    @FXML
    private Label lblSettings;
    @FXML
    private Label lblPdfOptions;
    @FXML
    private Label lblEditorOptions;
    @FXML
    private Label lblEditorFont;
    @FXML
    private Label lblFontSize;
    @FXML
    private Label lblBatchOptions;
    @FXML
    private Label lblTheme;
    @FXML
    private Label lblLanguage;
    @FXML
    private CheckBox cbAddPageHeader;
    @FXML
    private CheckBox cbCompactPdfText;
    @FXML
    private CheckBox cbAutoReflow;
    @FXML
    private Button btnRefresh;
    @FXML
    private Button btnSaveAs;
    @FXML
    private Button btnStart;
    @FXML
    private Button btnAdd;
    @FXML
    private Button btnRemove;
    @FXML
    private Button btnClearList;
    @FXML
    private Button btnPreviewSource;
    @FXML
    private Button btnSelectPath;
    @FXML
    private Button btnClearPreview;
    @FXML
    private RadioButton rbThemeSystem;
    @FXML
    private RadioButton rbThemeLight;
    @FXML
    private RadioButton rbThemeDark;
    @FXML
    private ToggleGroup themeGroup;
    @FXML
    private ComboBox<UiLanguage> cbLanguage;

    @FXML
    public void initialize() {
        String theme = AppPreferences.getSavedThemeMode();
        switch (theme) {
            case "dark":
                rbThemeDark.setSelected(true);
                break;
            case "light":
                rbThemeLight.setSelected(true);
                break;
            default:
                rbThemeSystem.setSelected(true);
                break;
        }
        applyCurrentTheme();

        themeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            if (rbThemeDark.isSelected()) {
                AppPreferences.saveThemeModeDark();
            } else if (rbThemeLight.isSelected()) {
                AppPreferences.saveThemeModeLight();
            } else {
                AppPreferences.saveThemeModeSystem();
            }
            applyCurrentTheme();
        });

        UiLanguage saved = AppPreferences.loadLanguagePreference();
        I18n.setLocale(saved.getLocale());
        ConversionComboBoxHelper.refreshLabels();
        cbLanguage.getItems().setAll(UiLanguage.values());
        cbLanguage.setValue(saved);

        cbLanguage.setOnAction(event -> {
            UiLanguage selected = cbLanguage.getValue();
            if (selected != null) {
                I18n.setLocale(selected.getLocale());
                ConversionComboBoxHelper.refreshLabels();
                applyTexts();
                applyStatusHover();
                updateRuntimeStatus();
                AppPreferences.saveLanguagePreference(selected);
            }
        });

        applyTexts();
        updateRuntimeStatus();
        ConversionComboBoxHelper.setupManualCombo(cbManual);
        ConversionComboBoxHelper.setupSaveTargetCombo(cbSaveTarget);

        cbLineNumber.setSelected(AppPreferences.getShowLineNumber());
        applyLineNumber(textAreaSource, cbLineNumber.isSelected());
        applyLineNumber(textAreaDestination, cbLineNumber.isSelected());
        cbLineNumber.selectedProperty().addListener((obs, oldVal, newVal) -> {
            applyLineNumber(textAreaSource, newVal);
            applyLineNumber(textAreaDestination, newVal);
            AppPreferences.saveShowLineNumber(newVal);
        });

        cbConvertFilename.setSelected(AppPreferences.getConvertFilename());
        cbConvertFilename.selectedProperty().addListener((obs, oldVal, newVal) -> AppPreferences.saveConvertFilename(newVal));
        // Hover status display
        applyStatusHover();
        initEditorFontControls();
        initUiButtons();
    }

    private void applyCurrentTheme() {
        Scene scene = rbThemeSystem.getScene();
        if (scene == null) return;
        Parent root = scene.getRoot();
        boolean dark = ThemeManager.isEffectiveDarkMode();
        ThemeManager.applyTheme(root, dark);
    }

    private void applyLineNumber(CodeArea area, boolean enabled) {
        if (enabled) {
            area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        } else {
            area.setParagraphGraphicFactory(null);
        }
    }

    private void applyTexts() {
        rbS2t.setText(I18n.get("config.s2t"));
        rbT2s.setText(I18n.get("config.t2s"));
        rbManual.setText(I18n.get("config.manual"));

        rbStd.setText(I18n.get("variant.general"));
        rbZHTW.setText(I18n.get("variant.tw"));
        rbHK.setText(I18n.get("variant.hk"));

        cbZHTW.setText(I18n.get("variant.twIdioms"));
        cbPunctuation.setText(I18n.get("option.punctuation"));

        tabMain.setText(I18n.get("tab.main"));
        tabBatch.setText(I18n.get("tab.batch"));
        tabSettings.setText(I18n.get("tab.settings"));

        lblSource.setText(I18n.get("label.source"));
        lblDestination.setText(I18n.get("label.destination"));

        lblOpenFile.setText(I18n.get("button.openFile"));
        lblStart.setText(I18n.get("button.start"));
        lblExit.setText(I18n.get("button.exit"));
        lblOutputFolder.setText(I18n.get("label.outputFolder"));
        textFieldPath.setPromptText(I18n.get("textField.outputFolder.prompt"));

        // Settings
        lblSettings.setText(I18n.get("label.settings"));
        lblEditorOptions.setText(I18n.get("label.editorOptions"));
        cbLineNumber.setText(I18n.get("checkbox.showLineNumber"));
        lblEditorFont.setText(I18n.get("label.editorFont"));
        lblFontSize.setText(I18n.get("label.size"));
        lblPdfOptions.setText(I18n.get("label.pdfOptions"));
        cbAddPageHeader.setText(I18n.get("checkbox.pageHeader"));
        cbCompactPdfText.setText(I18n.get("checkbox.compactText"));
        cbAutoReflow.setText(I18n.get("checkbox.autoReflowText"));
        lblBatchOptions.setText(I18n.get("label.batchOptions"));
        cbConvertFilename.setText(I18n.get("checkbox.convertFilename"));
        lblTheme.setText(I18n.get("label.theme"));
        rbThemeSystem.setText(I18n.get("radio.theme.system"));
        rbThemeLight.setText(I18n.get("radio.theme.light"));
        rbThemeDark.setText(I18n.get("radio.theme.dark"));
        lblLanguage.setText(I18n.get("label.language"));
    }

    private void updateRuntimeStatus() {
        lblStatus.setText(I18n.format(
                "status.runtime",
                System.getProperty("java.version"),
                I18n.get("app.title")
        ));
    }

    private void applyStatusHover() {
        StatusHoverHelper.bind(cbPunctuation, lblStatus, I18n.get("hint.punctuation"));
        StatusHoverHelper.bind(btnOpenFile, lblStatus, I18n.get("hint.openFile"));
        StatusHoverHelper.bind(btnRefresh, lblStatus, I18n.get("hint.refreshPdf"));
        StatusHoverHelper.bind(btnClearSource, lblStatus, I18n.get("hint.clearSource"));
        StatusHoverHelper.bind(btnPaste, lblStatus, I18n.get("hint.paste"));
        StatusHoverHelper.bind(cbSaveTarget, lblStatus, I18n.get("hint.saveTarget"));
        StatusHoverHelper.bind(btnSaveAs, lblStatus, I18n.get("hint.saveAs"));
        StatusHoverHelper.bind(btnClearDestination, lblStatus, I18n.get("hint.clearDestination"));
        StatusHoverHelper.bind(btnCopy, lblStatus, I18n.get("hint.copy"));
        StatusHoverHelper.bind(lblPdfOptions, lblStatus, I18n.get("hint.pdfOptions"));
        StatusHoverHelper.bind(lblFilename, lblStatus, lblFilename::getText);
        StatusHoverHelper.bind(btnAdd, lblStatus, I18n.get("hint.add"));
        StatusHoverHelper.bind(btnRemove, lblStatus, I18n.get("hint.remove"));
        StatusHoverHelper.bind(btnClearList, lblStatus, I18n.get("hint.clearList"));
        StatusHoverHelper.bind(btnPreviewSource, lblStatus, I18n.get("hint.previewSource"));
        StatusHoverHelper.bind(btnSelectPath, lblStatus, I18n.get("hint.selectPath"));
        StatusHoverHelper.bind(btnClearPreview, lblStatus, I18n.get("hint.clearPreview"));
        StatusHoverHelper.bind(btnStart, lblStatus, I18n.format("hint.start"));
        StatusHoverHelper.bind(btnExit, lblStatus, I18n.format("hint.exit"));
    }

    private void initEditorFontControls() {
        List<String> fonts = EditorFontHelper.getAvailableEditorFonts();
        String savedFontFamily = AppPreferences.getEditorFontFamily();
        String initialFontFamily = EditorFontHelper.resolveInitialEditorFontFamily(fonts, savedFontFamily);
        int savedFontSize = AppPreferences.getEditorFontSize();

        cbEditorFont.setItems(FXCollections.observableArrayList(fonts));
        cbEditorFont.setCellFactory(param -> EditorFontHelper.createFontListCell());
        cbEditorFont.setButtonCell(EditorFontHelper.createFontListCell());

        spnFontSize.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 40, savedFontSize)
        );
        spnFontSize.setEditable(true);

        cbEditorFont.setValue(initialFontFamily);
        spnFontSize.getValueFactory().setValue(savedFontSize);

        cbEditorFont.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty()) {
                AppPreferences.setEditorFontFamily(newValue);
            }
            applyEditorFontStyle();
        });

        spnFontSize.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                AppPreferences.setEditorFontSize(newValue);
            }
            applyEditorFontStyle();
        });

        applyEditorFontStyle();
    }

    private void applyEditorFontStyle() {
        String font = cbEditorFont.getValue();
        Integer sizeValue = spnFontSize.getValue();
        String finalStyle = EditorFontHelper.buildEditorFontStyle(
                font,
                sizeValue,
                AppPreferences.getEditorFontSize()
        );

        textAreaSource.setStyle(finalStyle);
        textAreaDestination.setStyle(finalStyle);
//        textAreaPreview.setStyle(finalStyle);
    }

    private final Label lblOpenFile = new Label();
    private final Label lblStart = new Label();
    private final Label lblExit = new Label();

    private void initUiButtons() {
        btnOpenFile.setGraphic(buildIconText(AppIconGlyph.OPEN_FILE, "button.openFile", 18, lblOpenFile));
        btnOpenFile.setText(null);

        btnStart.setGraphic(buildIconText(AppIconGlyph.PLAY, "button.start", 20, lblStart));
        btnStart.setText(null);

        btnExit.setGraphic(buildIconText(AppIconGlyph.POWER, "button.exit", 18, lblExit));
        btnExit.setText(null);

        // icon-only buttons unchanged
        btnRefresh.setGraphic(new SymbolIcon(AppIconGlyph.REFRESH, 20));
        btnClearSource.setGraphic(new SymbolIcon(AppIconGlyph.DELETE, 20));
        btnPaste.setGraphic(new SymbolIcon(AppIconGlyph.PASTE, 22));
        btnClearDestination.setGraphic(new SymbolIcon(AppIconGlyph.DELETE, 20));
        btnCopy.setGraphic(new SymbolIcon(AppIconGlyph.COPY, 22));
        btnSaveAs.setGraphic(new SymbolIcon(AppIconGlyph.SAVE, 22));
        btnAdd.setGraphic(new SymbolIcon(AppIconGlyph.ADD_TO, 20));
        btnRemove.setGraphic(new SymbolIcon(AppIconGlyph.REMOVE_FROM, 20));
        btnClearList.setGraphic(new SymbolIcon(AppIconGlyph.DELETE, 20));
        btnPreviewSource.setGraphic(new SymbolIcon(AppIconGlyph.PREVIEW, 20));
        btnSelectPath.setGraphic(new SymbolIcon(AppIconGlyph.FOLDER_OPEN, 20));
        btnClearPreview.setGraphic(new SymbolIcon(AppIconGlyph.DELETE, 20));
        tabMain.setGraphic(new SymbolIcon(AppIconGlyph.SYNC, 18));
        tabBatch.setGraphic(new SymbolIcon(AppIconGlyph.MOVE_TO_FOLDER, 20));
        tabSettings.setGraphic(new SymbolIcon(AppIconGlyph.SETTINGS, 18));
    }

    private HBox buildIconText(AppIconGlyph glyph, String textKey, double size, Label textRef) {
        SymbolIcon icon = new SymbolIcon(glyph, size);

        textRef.setText(I18n.get(textKey));
        textRef.getStyleClass().add("button-text");

        HBox box = new HBox(icon, textRef);
        box.getStyleClass().add("button-content");

        return box;
    }

    @FXML
    private void onPdfOptionsToggle() {
        boolean currentlyEnabled = !cbAddPageHeader.isDisable();
        boolean enable = !currentlyEnabled;

        setPdfOptionsEnabled(enable);

        lblStatus.setText(
                enable
                        ? I18n.get("status.pdfOptions.enabled")
                        : I18n.get("status.pdfOptions.disabled")
        );
    }

    @FXML
    private ProgressBar buildProgressBar;

    private void showProgressBarIndeterminate(String status) {
        lblStatus.setText(status);
        if (buildProgressBar != null) {
            buildProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            buildProgressBar.setVisible(true);
            buildProgressBar.setManaged(true);
        }
    }

    private void hideProgressBar(String status) {
        lblStatus.setText(status);
        if (buildProgressBar != null) {
            buildProgressBar.setVisible(false);
            buildProgressBar.setManaged(false);
        }
    }

    private boolean isOpenFileDisabled = false;
    private boolean isStartDisabled = false;
    private String openFileName;
    private final OpenCC openccInstance = new OpenCC();

    @FXML
    protected void onBtnPasteClick() {
        String inputText = getClipboardTextFx();
        if ((inputText == null) || inputText.isEmpty()) {
            lblStatus.setText(I18n.get("status.paste.empty"));
        } else {
            textAreaSource.replaceText(inputText);
            openFileName = "";
            updateSourceInfo(OpenCC.zhoCheck(inputText));
            lblFilename.setText("");
            lblStatus.setText(I18n.get("status.paste"));
        }
    }

    private String getClipboardTextFx() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            return clipboard.getString();
        } else {
            return "";
        }
    }

    @FXML
    protected void onBtnCopyClicked() {
        String text = textAreaDestination.getText();
        // null-safe + trim check
        if (text == null || text.trim().isEmpty()) {
            lblStatus.setText(I18n.get("status.copy.empty"));
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        lblStatus.setText(I18n.get("status.copy"));
    }

    // Helper method to get file extension
    private static String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < filename.length() - 1)
                ? filename.substring(dotIndex).toLowerCase()
                : "";
    }

    private static Path replaceExtension(Path original, String newExtensionWithDot) {
        String fileName = original.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;
        return original.resolveSibling(base + newExtensionWithDot);
    }

    private static String buildConvertedFilename(String originalFilename, String config) {
        if (originalFilename == null || originalFilename.isEmpty()) return config;

        int dotIndex = originalFilename.lastIndexOf('.');
        String baseName = (dotIndex >= 0) ? originalFilename.substring(0, dotIndex) : originalFilename;
        String extension = (dotIndex >= 0) ? originalFilename.substring(dotIndex) : "";

        return baseName + "_" + config + extension;
    }

    public void onBtnExitClicked(MouseEvent mouseEvent) {
        final Node source = (Node) mouseEvent.getSource();
        final Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }

    public void onBtnStartClicked() {
        // Main Conversion
        if (tabMain.isSelected()) {
            startMainConversion();
        }
        // batch Conversion
        if (tabBatch.isSelected()) {
            startBatchConversion();
        }
    }

    private void startMainConversion() {
        String fullText = textAreaSource.getText();
        if (fullText.isEmpty()) {
            lblStatus.setText(I18n.get("status.convert.empty"));
            return;
        }

        String inputText = textAreaSource.getSelectedText();
        boolean hasSelection = inputText != null && !inputText.isEmpty();

        if (!hasSelection) {
            inputText = fullText;
        }

        if (lblSourceCode.getText().isEmpty()) {
            updateSourceInfo(OpenCC.zhoCheck(fullText));
        }

        String config = getCurrentConfig();
        openccInstance.setConfig(config);

        long startTime = System.nanoTime();
        String convertedText = openccInstance.convert(inputText, cbPunctuation.isSelected());
        long endTime = System.nanoTime();

        long elapsedMillis = (endTime - startTime) / 1_000_000;

        textAreaDestination.replaceText(convertedText);

        if (rbManual.isSelected()) {
            String selectedCode = ConversionComboBoxHelper.getSelectedManualCode(cbManual);
            lblDestinationCode.setText(selectedCode != null ? selectedCode : config);
        } else {
            switch (currentSourceCode) {
                case 1: // source is Hant -> destination becomes Hans
                    lblDestinationCode.setText(I18n.get("lang.dest.hans"));
                    break;
                case 2: // source is Hans -> destination becomes Hant
                    lblDestinationCode.setText(I18n.get("lang.dest.hant"));
                    break;
                default:
                    lblDestinationCode.setText(I18n.get("lang.dest.non"));
                    break;
            }
        }

        lblStatus.setText(I18n.format(
                hasSelection ? "status.convert.selected" : "status.convert.full",
                elapsedMillis,
                config
        ));
    }

    private void startBatchConversion() {
        ObservableList<String> fileList = listViewSource.getItems();
        if (fileList.isEmpty()) {
            lblStatus.setText(I18n.get("status.batch.emptyFileList"));
            return;
        }

        String outputDirectory = textFieldPath.getText();
        Path outputDirectoryPath = Paths.get(outputDirectory);
        if (outputDirectory.isEmpty()) {
            textAreaPreview.appendText(I18n.get("preview.batch.outputDir.empty") + "\n");
            return;
        }
        if (!Files.isDirectory(outputDirectoryPath)) {
            textAreaPreview.appendText(
                    I18n.format("preview.batch.outputDir.notExist", outputDirectory) + "\n"
            );
            return;
        }

        // Prepare config once
        final String config = getCurrentConfig();
        openccInstance.setConfig(config);

        textAreaPreview.clear();
        lblStatus.setText(I18n.get("status.batch.running"));

        // Disable Start button while running (optional)
        btnStart.setDisable(true);

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                long startTime = System.currentTimeMillis();
                int counter = 0;

                for (String file : fileList) {
                    counter++;

                    String ext = getFileExtension(file).toLowerCase();   // e.g. ".pdf"
                    String extNoDot = ext.startsWith(".") ? ext.substring(1) : ext;  // "pdf"
                    File sourceFilePath = new File(file);

                    String outputFilename = buildConvertedFilename(sourceFilePath.getName(), config);
                    if (cbConvertFilename.isSelected()) {
                        outputFilename = openccInstance.convert(outputFilename);
                    }
                    Path outputFilePath = outputDirectoryPath.resolve(outputFilename);

                    try {
                        if (OfficeHelper.OFFICE_FORMATS.contains(extNoDot)) {
                            // Office
                            OfficeHelper.FileResult result = OfficeHelper.convert(
                                    sourceFilePath,
                                    outputFilePath.toFile(),
                                    extNoDot,
                                    openccInstance,
                                    cbPunctuation.isSelected(),
                                    true // Keep font
                            );

                            String statusText = result.success
                                    ? I18n.get("preview.batch.msg.done")
                                    : I18n.get("preview.batch.msg.skipped");

                            String msg = I18n.format(
                                    "preview.batch.office.done",
                                    counter,
                                    statusText,
                                    result.success ? outputFilePath : file,
                                    result.message
                            ) + "\n";

                            Platform.runLater(() -> textAreaPreview.appendText(msg));

                        } else if ("pdf".equals(extNoDot)) {
                            // PDF → extract + reflow + OpenCC → .txt
                            boolean autoReflow = cbAutoReflow.isSelected();
                            boolean addHeader = cbAddPageHeader.isSelected();
                            boolean compact = cbCompactPdfText.isSelected();

                            final int idx = counter;
                            Platform.runLater(() ->
                                    textAreaPreview.appendText(
                                            I18n.format("preview.batch.pdf.processing", idx) + "\n"
                                    ));

                            String raw = PdfBoxHelper.extractText(sourceFilePath, addHeader);

                            String reflowed = raw;
                            if (autoReflow) {
                                reflowed = PdfReflowHelper.reflowCjkParagraphs(raw, addHeader, compact);
                            }

                            String converted = openccInstance.convert(reflowed, cbPunctuation.isSelected());

                            String txtExt = ".txt";
                            Path txtOutputPath = replaceExtension(outputFilePath, txtExt);
                            Files.write(txtOutputPath, converted.getBytes(StandardCharsets.UTF_8));

//                            final int idx = counter;
                            final Path finalPath = txtOutputPath;
                            Platform.runLater(() ->
                                    textAreaPreview.appendText(
                                            I18n.format("preview.batch.done", idx, finalPath) + "\n"
                                    ));

                        } else if (FILE_EXTENSIONS.contains(ext)) {
                            // Plain text file
                            Path sourcePath = sourceFilePath.toPath();
//                            String contents = Files.readString(sourcePath, StandardCharsets.UTF_8);
                            String contents = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
                            String convertedText = openccInstance.convert(contents, cbPunctuation.isSelected());
                            Files.write(outputFilePath, convertedText.getBytes(StandardCharsets.UTF_8));

                            final int idx = counter;
                            final Path finalPath = outputFilePath;
                            Platform.runLater(() ->
                                    textAreaPreview.appendText(
                                            I18n.format("preview.batch.done", idx, finalPath) + "\n"
                                    ));

                        } else {
                            final int idx = counter;
                            final String fileName = file;
                            Platform.runLater(() ->
                                    textAreaPreview.appendText(
                                            I18n.format("preview.batch.skipped.invalidFormat", idx, fileName) + "\n"
                                    ));
                        }
                    } catch (Exception e) {
                        final int idx = counter;
                        final String fileName = file;
                        final String err = (e.getMessage() != null && !e.getMessage().trim().isEmpty())
                                ? e.getMessage()
                                : e.getClass().getSimpleName();

                        Platform.runLater(() ->
                                textAreaPreview.appendText(
                                        I18n.format("preview.batch.skipped.error", idx, fileName, err) + "\n"
                                ));
                    }
                }

                long endTime = System.currentTimeMillis();
                long elapsed = endTime - startTime;

                Platform.runLater(() -> {
                    String time = String.format("%,d", elapsed);
                    textAreaPreview.appendText(I18n.format("preview.batch.completed", time) + "\n");
                    lblStatus.setText(I18n.format("status.batch.completed", time));
                    btnStart.setDisable(false);
                });

                return null;
            }
        };

        Thread t = new Thread(task, "batch-convert-thread");
        t.setDaemon(true);
        t.start();
    }


    private void updateSourceInfo(int textCode) {
        currentSourceCode = textCode;

        switch (textCode) {
            case 1:
                rbT2s.setSelected(true);
                lblSourceCode.setText(I18n.get("lang.source.hant"));
                break;
            case 2:
                rbS2t.setSelected(true);
                lblSourceCode.setText(I18n.get("lang.source.hans"));
                break;
            default:
                lblSourceCode.setText(I18n.get("lang.source.non"));
        }

        lblSourceCharCount.setText(I18n.format(
                "status.charCount",
                textAreaSource.getText().length()
        ));

        if (openFileName != null) {
            lblFilename.setText(new File(openFileName).getName());
            lblStatus.setText(openFileName);
        } else {
            openFileName = "";
            lblFilename.setText(openFileName);
        }
    }

    private String getCurrentConfig() {
        return getCurrentConfigId().toCanonicalName();
    }

    private OpenccConfig getCurrentConfigId() {
        if (rbS2t.isSelected()) {
            if (rbStd.isSelected()) return OpenccConfig.S2T;
            if (rbHK.isSelected()) return OpenccConfig.S2HK;
            return cbZHTW.isSelected() ? OpenccConfig.S2TWP : OpenccConfig.S2TW;
        }

        if (rbT2s.isSelected()) {
            if (rbStd.isSelected()) return OpenccConfig.T2S;
            if (rbHK.isSelected()) return OpenccConfig.HK2S;
            return cbZHTW.isSelected() ? OpenccConfig.TW2SP : OpenccConfig.TW2S;
        }

        if (rbManual.isSelected()) {
            String code = ConversionComboBoxHelper.getSelectedManualCode(cbManual);
            OpenccConfig cfg = OpenccConfig.tryParse(code);
            if (cfg != null) return cfg;
        }
        return OpenccConfig.S2T; // fallback
    }

    public void onBtnOpenFileClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("status.openFile.title"));

        // Safer initial directory (JavaFX can throw if invalid)
        File dir = new File(System.getProperty("user.home"));
        if (dir.isDirectory()) {
            fileChooser.setInitialDirectory(dir);
        }

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.text"), "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.subtitle"), "*.srt", "*.vtt", "*.ass", "*.xml", "*.ttml2"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.word"), "*.docx", "*.odt"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.epub"), "*.epub"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.pdf"), "*.pdf"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.all"), "*.*")
        );

        // Use your window as owner (replace getStage() with your actual Stage getter)
        File selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null && selectedFile.isFile() && selectedFile.exists()) {
            startLoadFileTask(selectedFile);
        } else if (selectedFile != null) {
            lblStatus.setText(I18n.get("status.openFile.invalid"));
        }
    }

    // Example helper (use whatever you already have)
    private javafx.stage.Stage getStage() {
        return (javafx.stage.Stage) lblStatus.getScene().getWindow();
    }

    /**
     * Loads a text / PDF / DOCX / ODT file in a background thread.
     * <p>
     * UI behavior:
     * - Shows an indeterminate progress bar immediately.
     * - Performs file I/O, PDF extraction, and reflow off the JavaFX UI thread.
     * - When done, updates UI components back on the FX thread (via succeeded()).
     * - Ensures the UI stays responsive and progress bar animation remains visible.
     */
    private void startLoadFileTask(File file) {
        // Show progress bar immediately (UI thread)
        showProgressBarIndeterminate(I18n.format("status.loadFile", getFileExtension(file.toString())));

        Task<String> task = new Task<String>() {

            // These values are passed to succeeded() after background work finishes
            private boolean enablePdfOptions;
            private String statusAfter;

            @Override
            protected String call() throws Exception {
                // Heavy work runs here in a background thread
//                String fileNameLower = file.getName().toLowerCase(Locale.ROOT);
//                String path = file.getAbsolutePath();

                // -------- PDF Handling --------
                if (PdfBoxHelper.isPdf(file)) {
                    enablePdfOptions = true;

                    boolean autoReflow = cbAutoReflow.isSelected();
                    boolean addHeader = cbAddPageHeader.isSelected();
                    boolean compact = cbCompactPdfText.isSelected();

                    // Extract PDF text (with or without headers)
                    String raw = addHeader
                            ? PdfBoxHelper.extractTextWithHeaders(file)
                            : PdfBoxHelper.extractText(file);

                    // Optional CJK paragraph reflow
                    String finalText = raw;
                    if (autoReflow) {
                        finalText = PdfReflowHelper.reflowCjkParagraphs(raw, addHeader, compact);
                    }

//                    statusAfter = String.format("PDF loaded %s: %s", autoReflow ? "(Auto-Reflow)" : "", file);
                    statusAfter = I18n.format("status.loadFile.pdf", autoReflow ? I18n.get("status.autoReflow") : "", file);
                    return finalText;
                }

                // -------- DOCX Handling --------
//                if (fileNameLower.endsWith(".docx")) {
                if (OpenXmlHelper.isDocx(file)) {
                    enablePdfOptions = false;

                    // IMPORTANT: DOCX is a ZIP container — must extract via OpenXml helper
                    // includePartHeadings=false, normalizeNewlines=true is usually best for UI
                    String text = OpenXmlHelper.extractDocxAllText(
                            file.getAbsolutePath(),
                            false,  // includePartHeadings
                            true    // normalizeNewlines
                    );

                    statusAfter = I18n.format("status.loadFile.docx", file);
                    return text;
                }

                // -------- ODT Handling (optional, if you have it) --------
//                if (fileNameLower.endsWith(".odt")) {
                if (OpenXmlHelper.isOdt(file)) {
                    enablePdfOptions = false;

                    // If your ODT extractor is also in OpenXmlHelper, call it here.
                    // Otherwise, replace with your actual helper.
                    String text = OpenDocumentHelper.extractOdtAllText(file);

                    statusAfter = I18n.format("status.loadFile.odt", file);
                    return text;
                }

                // -------- ODT Handling (optional, if you have it) --------
                if (EpubHelper.isEpub(file)) {
                    enablePdfOptions = false;

                    String text = EpubHelper.extractEpubAllText(file.getAbsolutePath(),
                            false,
                            true,
                            true);

                    statusAfter = I18n.format("status.loadFile.epub", file);
                    return text;
                }

                // -------- Plain Text Handling --------
                enablePdfOptions = false;

                byte[] bytes = Files.readAllBytes(file.toPath());

                // Quick binary sniff: avoid showing ZIP/EXE garbage as "text"
                // (DOCX already handled above; this catches other binaries)
                int zeros = 0;
                int probe = Math.min(bytes.length, 4096);
                for (int i = 0; i < probe; i++) {
                    if (bytes[i] == 0) zeros++;
                }
                if (zeros > 0) {
                    throw new IOException("This file looks like a binary file (not plain text).");
                }

                String content = new String(bytes, StandardCharsets.UTF_8);

                // Remove BOM if present
                if (content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }

                statusAfter = I18n.format("status.loadFile.txt", file);
                return content;
            }

            @Override
            protected void succeeded() {
                // Now running back on the JavaFX UI thread
                try {
                    String text = get(); // get result from call()

                    // Enable or disable PDF controls depending on file type
                    setPdfOptionsEnabled(enablePdfOptions);

                    // Update UI content
                    textAreaSource.replaceText(text);
                    openFileName = file.toString();
                    updateSourceInfo(OpenCC.zhoCheck(text));

                    // Hide progress bar and set final status
                    hideProgressBar(statusAfter);

                } catch (Exception ex) {
                    failed(); // fallback to error handler
                }
            }

            @Override
            protected void failed() {
                // Runs on FX thread if background execution failed
                Throwable ex = getException();
                hideProgressBar("Error reading file.");

                lblStatus.setText("Error reading file: " +
                        (ex != null ? ex.getMessage() : "Unknown error"));
            }
        };

        // Start background thread
        Thread t = new Thread(task);
        t.setDaemon(true); // allow JVM to exit cleanly
        t.start();
    }


    public void onSourceTextChanged() {
        lblSourceCharCount.setText(I18n.format(
                "status.charCount",
                textAreaSource.getText().length()
        ));
    }

    public void onRbStdClicked() {
        cbZHTW.setSelected(false);
        cbZHTW.setDisable(true);
    }

    public void onRbZhtwClicked() {
        cbZHTW.setSelected(true);
        cbZHTW.setDisable(false);
    }

    public void onTabMainSelectionChanged() {
        if (!tabMain.isSelected()) {
            return;
        }
        if (isOpenFileDisabled) {
            btnOpenFile.setDisable(false);
            isOpenFileDisabled = false;
        }
        if (isStartDisabled) {
            btnStart.setDisable(false);
            isStartDisabled = false;
        }
    }

    public void onTabBatchSelectionChanged() {
        if (!tabBatch.isSelected()) {
            return;
        }
        btnOpenFile.setDisable(true);
        isOpenFileDisabled = true;
        if (isStartDisabled) {
            btnStart.setDisable(false);
            isStartDisabled = false;
        }
    }

    public void onTabSettingsSelectionChanged() {
        if (!tabSettings.isSelected()) {
            return;
        }
        btnOpenFile.setDisable(true);
        isOpenFileDisabled = true;
        btnStart.setDisable(true);
        isStartDisabled = true;
    }

    public void onBtnAddClicked() {
        ObservableList<String> currentFileList = listViewSource.getItems();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("status.add.title"));
        fileChooser.setInitialDirectory(new File("."));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.get("status.add.filter.text"), "*.txt"),
                new FileChooser.ExtensionFilter(I18n.get("status.add.filter.subtitle"), Arrays.asList("*.srt", "*.vtt", "*.ass", "*.xml", "*.ttml2")),
                new FileChooser.ExtensionFilter(I18n.get("status.add.filter.office"), Arrays.asList("*.docx", "*.xlsx", "*.pptx", "*.odt", "*.ods", "*.odp", "*.epub")),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.pdf"), "*.pdf"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.all"), "*.*")
        );

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
        if (selectedFiles != null) {
            int count = 0;

            for (File file : selectedFiles) {
                String path = file.getAbsolutePath();
                if (!currentFileList.contains(path)) {
                    currentFileList.add(path);
                    count++;
                }
            }
            // --- Sort: non-PDF first, PDF last ---
            List<String> pdfList = new ArrayList<>();
            List<String> otherList = new ArrayList<>();

            for (String path : currentFileList) {
                if (getFileExtension(path).equals(".pdf")) {
                    pdfList.add(path);
                } else {
                    otherList.add(path);
                }
            }
            // Sort only non-PDF files alphabetically
            Collections.sort(otherList);
            // Merge back: others first, then PDFs
            ObservableList<String> merged = FXCollections.observableArrayList();
            merged.addAll(otherList);
            merged.addAll(pdfList);

            listViewSource.setItems(merged);
            lblStatus.setText(I18n.format("status.add.added", count));
        }
    }

    public void onBtnRemoveClicked() {
        ObservableList<String> currentFileList = listViewSource.getItems();
        String selectedItem = listViewSource.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            currentFileList.remove(selectedItem);
            lblStatus.setText(I18n.format("status.remove.removed", selectedItem));
        }
        listViewSource.setItems(currentFileList);
    }

    public void onBtnClearListClicked() {
        ObservableList<String> currentFileList = listViewSource.getItems();
        currentFileList.clear();
        listViewSource.setItems(currentFileList);
        lblStatus.setText(I18n.get("status.clearList"));
    }

    public void onBtnPreviewSourceClicked() {
        String selectedItem = listViewSource.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            File file = new File(selectedItem);
            String fileExtension = getFileExtension(file.getName());
            if (file.isFile() && FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String content = new String(bytes, StandardCharsets.UTF_8);
//                    textAreaPreview.setText(content);
                    textAreaPreview.replaceText(content);
                    lblStatus.setText(I18n.format("status.previewSource.success", selectedItem));
                } catch (IOException e) {
                    textAreaPreview.replaceText(I18n.format("status.previewSource.readError", e.getMessage()));
                }
            } else {
                textAreaPreview.replaceText(I18n.format("status.previewSource.invalidTextFile", getFileExtension(file.getName())));
            }
        }
    }

    public void onBtnSelectPathClicked() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(I18n.get("status.selectPath.title"));
        directoryChooser.setInitialDirectory(new File("."));
        File selectedDirectory = directoryChooser.showDialog(null);
        if (selectedDirectory != null) {
            textFieldPath.setText(selectedDirectory.getAbsolutePath());
            lblStatus.setText(I18n.format("status.selectPath.selected", selectedDirectory));
        }
    }

    public void onBtnClearPreviewClicked() {
        textAreaPreview.clear();
        textAreaPreview.getUndoManager().forgetHistory();
        lblStatus.setText(I18n.get("status.clearPreview"));
    }

    @FXML
    public void onBtnSaveAsClicked() {
        String key = ConversionComboBoxHelper.getSelectedSaveTargetKey(cbSaveTarget);
        String targetLabel = ConversionComboBoxHelper.getSelectedSaveTargetLabel(cbSaveTarget);

        if (key == null) {
            lblStatus.setText(I18n.get("status.save.target.notSelected"));
            return;
        }

        String content;
        String suggestedName;

        switch (key) {
            case "source":
                content = textAreaSource.getText();
                suggestedName = "Source.txt";
                break;

            case "destination":
                content = textAreaDestination.getText();
                suggestedName = "Destination.txt";
                break;

            default:
                lblStatus.setText(I18n.get("status.save.unknownTarget"));
                return;
        }

        if (content == null || content.isEmpty()) {
            lblStatus.setText(I18n.format("status.save.empty", targetLabel));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("dialog.save.title"));
        fileChooser.setInitialDirectory(new File("."));
        fileChooser.setInitialFileName(suggestedName);

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.get("status.add.filter.text"), "*.txt"),
                new FileChooser.ExtensionFilter(I18n.get("status.add.filter.subtitle"),
                        "*.srt", "*.vtt", "*.ass", "*.xml", "*.ttml2"),
                new FileChooser.ExtensionFilter(I18n.get("status.openFile.filter.all"), "*.*")
        );

        File selectedFile = fileChooser.showSaveDialog(null);

        if (selectedFile != null) {
            try {
                Files.write(selectedFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

                lblStatus.setText(
                        I18n.format("status.save.success",
                                targetLabel,
                                selectedFile.getAbsolutePath())
                );

            } catch (Exception e) {
                lblStatus.setText(
                        I18n.format("status.save.error",
                                targetLabel,
                                selectedFile.getAbsolutePath())
                );
            }
        }
    }

    private void setPdfOptionsEnabled(boolean enabled) {
        cbAddPageHeader.setDisable(!enabled);
        cbCompactPdfText.setDisable(!enabled);
        cbAutoReflow.setDisable(!enabled);
    }

    public void onTaSourceDragOver(DragEvent dragEvent) {
        Dragboard dragboard = dragEvent.getDragboard();
        if (dragEvent.getGestureSource() != textAreaSource &&
                (dragboard.hasFiles() || dragboard.hasString())) {
            dragEvent.acceptTransferModes(TransferMode.COPY);
        }
        dragEvent.consume();
    }

    public void onTaDragDropped(DragEvent dragEvent) {
        Dragboard dragboard = dragEvent.getDragboard();
        boolean success = false;
        if (dragboard.hasFiles()) {
            File file = dragboard.getFiles().get(0);
            if (isPdfFile(file) || OpenXmlHelper.isDocx(file) || OpenXmlHelper.isOdt(file) || EpubHelper.isEpub(file)) {
                startLoadFileTask(file);
            } else if (isTextFile(file)) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    StringBuilder content = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    reader.close();
                    String text = content.toString();
                    // Remove BOM if present
                    if (text.startsWith("\uFEFF")) {
                        text = text.substring(1);
                    }
                    textAreaSource.replaceText(text);
                    openFileName = file.toString();
                    updateSourceInfo(OpenCC.zhoCheck(text));
                    success = true;
                } catch (Exception e) {
                    lblStatus.setText(I18n.format("status.dnd.textArea.error", e.getMessage()));
                }
            } else {
                textAreaSource.replaceText(I18n.get("status.dnd.textArea.invalidTextFile"));
            }
        } else if (dragboard.hasString()) {
            // User dragged in plain text
            String text = dragboard.getString();

            // Remove BOM if present (just in case)
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }

            textAreaSource.replaceText(text);
            openFileName = I18n.get("status.dnd.textArea.droppedText");
            updateSourceInfo(OpenCC.zhoCheck(text));
            success = true;
        }
        dragEvent.setDropCompleted(success);
        dragEvent.consume();
    }

    private boolean isTextFile(File file) {
        String fileExtension = getFileExtension(file.getName());
        return file.isFile() && FILE_EXTENSIONS.contains(fileExtension.toLowerCase(Locale.ROOT));
    }

    private boolean isOfficeFile(File file) {
        String fileExtension = getFileExtension(file.getName());
        return file.isFile() && OfficeHelper.OFFICE_FORMATS.contains(fileExtension.toLowerCase(Locale.ROOT).substring(1));
    }

    private static boolean isPdfFile(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf");
    }


    public void onLivSourceDragOver(DragEvent dragEvent) {
        if (dragEvent.getGestureSource() != listViewSource && dragEvent.getDragboard().hasFiles()) {
            dragEvent.acceptTransferModes(TransferMode.COPY);
        }
        dragEvent.consume();
    }

    public void onLvDragDropped(DragEvent dragEvent) {
        Dragboard dragboard = dragEvent.getDragboard();
        boolean success = false;
        if (dragboard.hasFiles()) {
            ObservableList<String> fileList = listViewSource.getItems();
            List<File> files = dragboard.getFiles();
            int count = 0;
            for (File file : files) {
                if ((isTextFile(file) || isOfficeFile(file) || isPdfFile(file)) && !fileList.contains(file.getAbsolutePath())) {
                    fileList.add(file.getAbsolutePath());
                    count++;
                    success = true;
                }
            }
            FXCollections.sort(fileList, null);
            listViewSource.setItems(fileList);
            lblStatus.setText(I18n.format("status.dnd.listBox.added", count));
        }
        dragEvent.setDropCompleted(success);
        dragEvent.consume();
    }

    public void onBthRefreshClicked() {
        String sourceText = textAreaSource.getText();
        if (sourceText == null || sourceText.trim().isEmpty()) {
            lblStatus.setText(I18n.get("status.reflow.empty"));
            return;
        }

        // 根據 PDF Options checkbox 做 reflow 行為
        boolean addHeader = cbAddPageHeader.isSelected();
        boolean compact = cbCompactPdfText.isSelected();

        // 用你嘅 Java 版 Reflow helper
        String reflowed = PdfReflowHelper.reflowCjkParagraphs(sourceText, addHeader, compact);

        // 回寫到 Source textbox
        textAreaSource.replaceText(reflowed);

        // 更新 Source info（字數 / 語種等）
        updateSourceInfo(OpenCC.zhoCheck(reflowed));

        lblStatus.setText(I18n.get("status.reflow"));
    }

    public void onCbManualClicked() {
        rbManual.setSelected(true);
    }

    public void onBthClearSourceClicked() {
        textAreaSource.clear();
        textAreaSource.getUndoManager().forgetHistory();
        openFileName = "";
        lblFilename.setText("");
        lblSourceCode.setText("");
        lblSourceCharCount.setText("");
        lblStatus.setText(I18n.get("status.clearSource"));
    }

    public void onBthClearDestinationClicked() {
        textAreaDestination.clear();
        textAreaDestination.getUndoManager().forgetHistory();
        lblDestinationCode.setText("");
        lblStatus.setText(I18n.get("status.clearDestination"));
    }
} // class DemoFxController
