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
import org.example.openccjavafx.ui.EditorFontHelper;
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

    private static final List<ConfigItem> CONFIG_LIST = Arrays.asList(
            new ConfigItem("s2t", "config.manual.s2t"),
            new ConfigItem("s2tw", "config.manual.s2tw"),
            new ConfigItem("s2twp", "config.manual.s2twp"),
            new ConfigItem("s2hk", "config.manual.s2hk"),
            new ConfigItem("t2s", "config.manual.t2s"),
            new ConfigItem("t2tw", "config.manual.t2tw"),
            new ConfigItem("t2twp", "config.manual.t2twp"),
            new ConfigItem("t2hk", "config.manual.t2hk"),
            new ConfigItem("tw2s", "config.manual.tw2s"),
            new ConfigItem("tw2sp", "config.manual.tw2sp"),
            new ConfigItem("tw2t", "config.manual.tw2t"),
            new ConfigItem("tw2tp", "config.manual.tw2tp"),
            new ConfigItem("hk2s", "config.manual.hk2s"),
            new ConfigItem("hk2t", "config.manual.hk2t"),
            new ConfigItem("t2jp", "config.manual.t2jp"),
            new ConfigItem("jp2t", "config.manual.jp2t")
    );

    private static final List<String> SAVE_TARGET_LIST = Arrays.asList(
            "Source",
            "Destination");

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
    private Label lblSourceCode;
    @FXML
    private Label lblDestinationCode;
    @FXML
    private Label lblSourceCharCount;
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
    private ComboBox<String> cbSaveTarget;
    @FXML
    private ComboBox<String> cbEditorFont;
    @FXML
    private Spinner<Integer> spnFontSize;
    @FXML
    private Label lblPdfOptions;
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
        cbLanguage.getItems().setAll(UiLanguage.values());
        cbLanguage.setValue(saved);

        cbLanguage.setOnAction(event -> {
            UiLanguage selected = cbLanguage.getValue();
            if (selected != null) {
                I18n.setLocale(selected.getLocale());
                applyTexts();
                updateRuntimeStatus();
                AppPreferences.saveLanguagePreference(selected);
            }
        });

        applyTexts();
        updateRuntimeStatus();

        cbManual.getItems().addAll(CONFIG_LIST);
        cbManual.getSelectionModel().selectFirst();
        cbLineNumber.setSelected(AppPreferences.getShowLineNumber());

        applyLineNumber(textAreaSource, cbLineNumber.isSelected());
        applyLineNumber(textAreaDestination, cbLineNumber.isSelected());

        cbLineNumber.selectedProperty().addListener((obs, oldVal, newVal) -> {
            applyLineNumber(textAreaSource, newVal);
            applyLineNumber(textAreaDestination, newVal);
            AppPreferences.saveShowLineNumber(newVal);
        });

//        textAreaSource.setParagraphGraphicFactory(LineNumberFactory.get(textAreaSource));
//        textAreaDestination.setParagraphGraphicFactory(LineNumberFactory.get(textAreaDestination));

        cbConvertFilename.setSelected(AppPreferences.getConvertFilename());
        cbConvertFilename.selectedProperty().addListener((obs, oldVal, newVal) -> AppPreferences.saveConvertFilename(newVal));
        cbSaveTarget.getItems().addAll(SAVE_TARGET_LIST);
        cbSaveTarget.getSelectionModel().select(1);
        // Hover status display
        applyStatusHover();
        initEditorFontControls();
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
    }

    private void updateRuntimeStatus() {
        lblStatus.setText(I18n.format(
                "status.runtime",
                I18n.get("app.title"),
                System.getProperty("java.version")
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
//        StatusHoverHelper.bind(btnStart, lblStatus, "Start convert text with OpenccJava");
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
        textAreaPreview.setStyle(finalStyle);
    }

    @FXML
    private void onPdfOptionsToggle() {
        // 睇下依家係咪 enabled（用其中一個 checkbox 作準）
        boolean currentlyEnabled = !cbAddPageHeader.isDisable();
        boolean enable = !currentlyEnabled;

        setPdfOptionsEnabled(enable);

        lblStatus.setText(enable
                ? "PDF options enabled."
                : "PDF options disabled.");
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
            lblStatus.setText("Clipboard is empty or could not retrieve contents.");
        } else {
            textAreaSource.replaceText(inputText);
            openFileName = "";
            updateSourceInfo(OpenCC.zhoCheck(inputText));
            lblFilename.setText("");
            lblStatus.setText("Clipboard contents pasted to source area.");
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
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(textAreaDestination.getText());
        clipboard.setContent(content);
        lblStatus.setText("Destination contents copied to clipboard.");
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
            lblStatus.setText("Nothing to convert.");
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
            ConfigItem selected = cbManual.getValue();
            lblDestinationCode.setText(selected != null ? selected.getCode() : config);
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
            lblStatus.setText("Empty file list.\n");
            return;
        }

        String outputDirectory = textFieldPath.getText();
        Path outputDirectoryPath = Paths.get(outputDirectory);
        if (outputDirectory.isEmpty()) {
            textAreaPreview.appendText("Output directory is empty.\n");
            return;
        }
        if (!Files.isDirectory(outputDirectoryPath)) {
            textAreaPreview.appendText(String.format("Output directory [ %s ] does not exist.\n", outputDirectory));
            return;
        }

        // Prepare config once
        final String config = getCurrentConfig();
        openccInstance.setConfig(config);

        textAreaPreview.clear();
        lblStatus.setText("Batch conversion in progress...");

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

                            final String msg = String.format(
                                    "[%d][%s] %s -> %s%n",
                                    counter,
                                    result.success ? "Done" : "Skipped",
                                    result.success ? outputFilePath : file,
                                    result.message
                            );
                            Platform.runLater(() -> textAreaPreview.appendText(msg));

                        } else if ("pdf".equals(extNoDot)) {
                            // PDF → extract + reflow + OpenCC → .txt
                            boolean autoReflow = cbAutoReflow.isSelected();
                            boolean addHeader = cbAddPageHeader.isSelected();
                            boolean compact = cbCompactPdfText.isSelected();

                            final int idx = counter;
                            Platform.runLater(() ->
                                    textAreaPreview.appendText(String.format("[%d] Processing PDF... Please wait...%n", idx)));

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
                                    textAreaPreview.appendText(String.format("[%d][Done] -> %s%n", idx, finalPath)));

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
                                    textAreaPreview.appendText(String.format("[%d][Done] -> %s%n", idx, finalPath)));

                        } else {
                            final int idx = counter;
                            final String fileName = file;
                            Platform.runLater(() ->
                                    textAreaPreview.appendText(String.format(
                                            "[%d][Skipped] %s -> Not a valid file format%n",
                                            idx, fileName)));
                        }
                    } catch (Exception e) {
                        final int idx = counter;
                        final String fileName = file;
                        final String err = e.getMessage();
                        Platform.runLater(() ->
                                textAreaPreview.appendText(String.format(
                                        "[%d][Skipped] %s -> Error: %s%n",
                                        idx, fileName, err)));
                    }
                }

                long endTime = System.currentTimeMillis();
                long elapsed = endTime - startTime;

                Platform.runLater(() -> {
                    textAreaPreview.appendText(String.format("Process completed in %,d ms.%n", elapsed));
                    lblStatus.setText(String.format("Batch conversion completed in %,d ms.", elapsed));
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
            String v = cbManual.getValue().getCode();
            if (v != null) {
                int sp = v.indexOf(' ');
                String key = sp >= 0 ? v.substring(0, sp) : v;
                OpenccConfig cfg = OpenccConfig.tryParse(key);
                if (cfg != null) return cfg;
            }
        }

        return OpenccConfig.S2T; // fallback
    }

    public void onBtnOpenFileClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Text or PDF File");

        // Safer initial directory (JavaFX can throw if invalid)
        File dir = new File(System.getProperty("user.home"));
        if (dir.isDirectory()) {
            fileChooser.setInitialDirectory(dir);
        }

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("Subtitle Files", "*.srt", "*.vtt", "*.ass", "*.xml", "*.ttml2"),
                new FileChooser.ExtensionFilter("Word Documents", "*.docx", "*.odt"),
                new FileChooser.ExtensionFilter("Epub Files", "*.epub"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Use your window as owner (replace getStage() with your actual Stage getter)
        File selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null && selectedFile.isFile() && selectedFile.exists()) {
            startLoadFileTask(selectedFile);
        } else if (selectedFile != null) {
            lblStatus.setText("Selected file is not valid.");
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
        showProgressBarIndeterminate(String.format("Loading file ( %s ) ...", getFileExtension(file.toString())));

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

                    statusAfter = String.format("PDF loaded %s: %s", autoReflow ? "(Auto-Reflow)" : "", file);
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

                    statusAfter = String.format("DOCX loaded: %s", file);
                    return text;
                }

                // -------- ODT Handling (optional, if you have it) --------
//                if (fileNameLower.endsWith(".odt")) {
                if (OpenXmlHelper.isOdt(file)) {
                    enablePdfOptions = false;

                    // If your ODT extractor is also in OpenXmlHelper, call it here.
                    // Otherwise, replace with your actual helper.
                    String text = OpenDocumentHelper.extractOdtAllText(file);

                    statusAfter = String.format("ODT loaded: %s", file);
                    return text;
                }

                // -------- ODT Handling (optional, if you have it) --------
                if (EpubHelper.isEpub(file)) {
                    enablePdfOptions = false;

                    String text = EpubHelper.extractEpubAllText(file.getAbsolutePath(),
                            false,
                            true,
                            true);

                    statusAfter = String.format("Epub loaded: %s", file);
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

                statusAfter = String.format("Text file loaded: %s", file);
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
        lblSourceCharCount.setText(String.format("[ %,d chars ]", textAreaSource.getText().length()));
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
        fileChooser.setTitle("Select Files");
        fileChooser.setInitialDirectory(new File("."));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("Subtitle Files", Arrays.asList("*.srt", "*.vtt", "*.ass", "*.xml", "*.ttml2")),
                new FileChooser.ExtensionFilter("Office Files", Arrays.asList("*.docx", "*.xlsx", "*.pptx", "*.odt", "*.ods", "*.odp", "*.epub")),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
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
            lblStatus.setText(String.format("No of file added to list: %d", count));
        }
    }

    public void onBtnRemoveClicked() {
        ObservableList<String> currentFileList = listViewSource.getItems();
        String selectedItem = listViewSource.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            currentFileList.remove(selectedItem);
            lblStatus.setText(String.format("[%s] removed.", selectedItem));
        }
        listViewSource.setItems(currentFileList);
    }

    public void onBtnClearListClicked() {
        ObservableList<String> currentFileList = listViewSource.getItems();
        currentFileList.clear();
        listViewSource.setItems(currentFileList);
        lblStatus.setText("File list cleared.");
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
                    lblStatus.setText(String.format("File Preview: %s", selectedItem));
                } catch (IOException e) {
                    textAreaPreview.replaceText("Error reading file: " + e.getMessage());
                }
            } else {
                textAreaPreview.replaceText(String.format("Selected file (%s) is not a valid text file.", getFileExtension(file.getName())));
            }
        }
    }

    public void onBtnSelectPathClicked() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory");
        directoryChooser.setInitialDirectory(new File("."));
        File selectedDirectory = directoryChooser.showDialog(null);
        if (selectedDirectory != null) {
            textFieldPath.setText(selectedDirectory.getAbsolutePath());
            lblStatus.setText("Output path set to: " + selectedDirectory);
        }
    }

    public void onBtnClearPreviewClicked() {
        textAreaPreview.clear();
        textAreaPreview.getUndoManager().forgetHistory();
        lblStatus.setText("Preview cleared.");
    }

    public void onBtnSaveAsClicked() {
        String target = cbSaveTarget.getValue();
        if (target == null) {
            lblStatus.setText("Please select a save target.");
            return;
        }

        // 根據 comboBox 決定用邊個 TextArea
        String content;
        String suggestedName;
        switch (target) {
            case "Source":
                content = textAreaSource.getText();
                suggestedName = "Source.txt";
                break;
            case "Destination":
                content = textAreaDestination.getText();
                suggestedName = "Destination.txt";
                break;
            default:
                lblStatus.setText("Unknown save target.");
                return;
        }

        if (content == null || content.isEmpty()) {
            lblStatus.setText(target + " content is empty.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Text File");
        fileChooser.setInitialDirectory(new File(".")); // current dir
        fileChooser.setInitialFileName(suggestedName);

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("Subtitle Files",
                        "*.srt", "*.vtt", "*.ass", "*.xml", "*.ttml2"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showSaveDialog(null);

        if (selectedFile != null) {
            try {
                // Java 8 friendly 寫法
                Files.write(selectedFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                lblStatus.setText(String.format("%s saved to: %s", target, selectedFile));
            } catch (Exception e) {
                lblStatus.setText(String.format("Error saving %s: %s", target, selectedFile));
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
                    lblStatus.setText("Error: " + e.getMessage());
                }
            } else {
                textAreaSource.replaceText("Not a valid text file.");
            }
        } else if (dragboard.hasString()) {
            // User dragged in plain text
            String text = dragboard.getString();

            // Remove BOM if present (just in case)
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }

            textAreaSource.replaceText(text);
            openFileName = "<Dropped text>";
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
            lblStatus.setText(String.format("Total file added: %d", count));
        }
        dragEvent.setDropCompleted(success);
        dragEvent.consume();
    }

    public void onBthRefreshClicked() {
        String sourceText = textAreaSource.getText();
        if (sourceText == null || sourceText.trim().isEmpty()) {
            lblStatus.setText("Source text is empty, nothing to reflow.");
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

        lblStatus.setText("Source text has been reflowed.");
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
        lblStatus.setText("Source content has been cleared.");
    }

    public void onBthClearDestinationClicked() {
        textAreaDestination.clear();
        textAreaDestination.getUndoManager().forgetHistory();
        lblDestinationCode.setText("");
        lblStatus.setText("Destination content has been cleared.");
    }
} // class DemoFxController
