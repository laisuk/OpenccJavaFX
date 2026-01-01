package org.example.openccjavafx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.ProgressBar;
import openccjava.OfficeHelper;
import openxmlhelper.OpenDocumentHelper;
import openxmlhelper.OpenXmlHelper;
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
import pdfboxhelper.PdfBoxHelper;
import pdfboxhelper.PdfReflowHelper;
//import org.fxmisc.richtext.LineNumberFactory;

public class OpenccJavaFxController {
    private static final Set<String> FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".txt", ".xml", ".srt", ".ass", ".vtt", ".json", ".ttml2",
            ".csv", ".java", ".md", ".html", ".cs", ".py", ".cpp"
    ));

    private static final List<String> CONFIG_LIST = Arrays.asList(
            "s2t (简->繁)",
            "s2tw (简->繁台",
            "s2twp (简->繁台/惯)",
            "s2hk (简->繁港)",
            "t2s (繁->简)",
            "t2tw (繁->繁台)",
            "t2twp (繁->繁台/惯)",
            "t2hk (繁->繁港)",
            "tw2s (繁台->简)",
            "tw2sp (繁台->简/惯)",
            "tw2t (繁台->繁)",
            "tw2tp (繁台->繁/惯)",
            "hk2s (繁港->简)",
            "hk2t (繁港->繁)",
            "t2jp (日舊->日新)",
            "jp2t (日新->日舊)");

    private static final List<String> SAVE_TARGET_LIST = Arrays.asList(
            "Source",
            "Destination");

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
    private RadioButton rbHK;
    @FXML
    private CheckBox cbZHTW;
    @FXML
    private CheckBox cbPunctuation;
    @FXML
    private CheckBox cbConvertFilename;
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
    private Button btnOpenFile;
    @FXML
    private ListView<String> listViewSource;
    @FXML
    private TextField textFieldPath;
    @FXML
    private ComboBox<String> cbManual;
    @FXML
    private ComboBox<String> cbSaveTarget;
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
    public void initialize() {
        cbManual.getItems().addAll(CONFIG_LIST);
        cbManual.getSelectionModel().selectFirst();
//        textAreaSource.setParagraphGraphicFactory(LineNumberFactory.get(textAreaSource));
//        textAreaDestination.setParagraphGraphicFactory(LineNumberFactory.get(textAreaDestination));
        cbSaveTarget.getItems().addAll(SAVE_TARGET_LIST);
        cbSaveTarget.getSelectionModel().select(1);
        // Hover status display
        StatusHoverHelper.bind(btnOpenFile, lblStatus, "Open a file");
        StatusHoverHelper.bind(btnSaveAs, lblStatus, "Save current text in Source/Destination");
        StatusHoverHelper.bind(cbSaveTarget, lblStatus, "Select target text for saving");
        StatusHoverHelper.bind(btnRefresh, lblStatus, "Reflow PDF CJK Text ");
        StatusHoverHelper.bind(lblPdfOptions, lblStatus, "Click to toggle PDF options");
//        StatusHoverHelper.bind(btnStart, lblStatus, "Start convert text with OpenccJava");
        String javaVersion = System.getProperty("java.version");
        lblStatus.setText("OpenccJavaFX @ Java " + javaVersion);
        setPdfOptionsEnabled(false);
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
        String inputText = textAreaSource.getText();
        if (inputText.isEmpty()) {
            lblStatus.setText("Nothing to convert.");
            return;
        }

        if (lblSourceCode.getText().isEmpty()) {
            updateSourceInfo(OpenCC.zhoCheck(inputText));
        }

        String config = getConfig();
        openccInstance.setConfig(config);

        long startTime = System.nanoTime(); // Start timer before convert()
        String convertedText = openccInstance.convert(inputText, cbPunctuation.isSelected());
        long endTime = System.nanoTime();   // End timer after convert()

        long elapsedMillis = (endTime - startTime) / 1_000_000;

        textAreaDestination.replaceText(convertedText);

        if (rbManual.isSelected()) {
            lblDestinationCode.setText(cbManual.getValue() != null ? cbManual.getValue() : config);
        } else if (!lblSourceCode.getText().contains("non") && !lblSourceCode.getText().isEmpty()) {
            lblDestinationCode.setText(lblSourceCode.getText().contains("Hans") ? "zh-Hant (繁体)" : "zh-Hans (简体)");
        } else {
            lblDestinationCode.setText(lblSourceCode.getText());
        }

        lblStatus.setText(String.format("Conversion completed in %,d ms. [ %s ]", elapsedMillis, config));
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
        final String config = getConfig();
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
        switch (textCode) {
            case 1:
                rbT2s.setSelected(true);
                lblSourceCode.setText("zh-Hant (繁体)");
                break;
            case 2:
                rbS2t.setSelected(true);
                lblSourceCode.setText("zh-Hans (简体)");
                break;
            default:
                lblSourceCode.setText("non-zho (其它)");
        }
        lblSourceCharCount.setText(String.format("[ %,d chars ]", textAreaSource.getText().length()));
        if (openFileName != null) {
            lblFilename.setText(new File(openFileName).getName());
            lblStatus.setText(openFileName);
        } else {
            openFileName = "";
            lblFilename.setText(openFileName);
        }
    }

    private String getConfig() {
        String config = "s2t";
        if (rbS2t.isSelected()) {
            config = rbStd.isSelected() ? "s2t" : (rbHK.isSelected() ? "s2hk" : (cbZHTW.isSelected() ? "s2twp" : "s2tw"));
        }
        if (rbT2s.isSelected()) {
            config = rbStd.isSelected() ? "t2s" : (rbHK.isSelected() ? "hk2s" : (cbZHTW.isSelected() ? "tw2sp" : "tw2s"));
        }
        if (rbManual.isSelected()) {
            if (cbManual.getValue() != null) {
                config = cbManual.getValue().split(" ")[0];
            }
        }
        return config;
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
    }

    public void onTabBatchSelectionChanged() {
        if (!tabBatch.isSelected()) {
            return;
        }
        if (tabBatch.isSelected()) {
            btnOpenFile.setDisable(true);
            isOpenFileDisabled = true;
        }
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
            if (isPdfFile(file)) {
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