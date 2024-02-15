package org.example.demofx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.example.demofx.ZhoConverter.*;

public class DemoFxController {
    // List of desired file extensions
    private static final List<String> FILE_EXTENSIONS = Arrays.asList(".txt", ".xml", ".srt", ".ass", ".vtt", ".json", ".ttml2", ".csv");
    @FXML
    private TextArea textAreaSource;
    @FXML
    private TextArea textAreaDestination;
    @FXML
    private TextArea textAreaPreview;
    @FXML
    private RadioButton rbS2t;
    @FXML
    private RadioButton rbT2s;
    @FXML
    private RadioButton rbStd;
    @FXML
    private RadioButton rbHK;
    @FXML
    private CheckBox cbZHTW;
    @FXML
    private CheckBox cbPunctuation;
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
    private boolean isOpenFileDisabled = false;

    @FXML
    protected void onBtnPasteClick() {
        var inputText = getClipboardTextFx();
        textAreaSource.setText(inputText);
        updateSourceInfo(zhoCheck(inputText));
        lblFilename.setText("");
        lblStatus.setText("Clipboard contents pasted to source area.");
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
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex != -1) {
            return fileName.substring(lastDotIndex);
        }
        return null;
    }

    private String getClipboardTextFx() {
        // Create a Clipboard object
        Clipboard clipboard = Clipboard.getSystemClipboard();
        // Get the content from the clipboard
        if (clipboard.hasString()) {
            // Retrieve and print the text content
            return clipboard.getString();
        } else {
            return "";
        }
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
        var config = getConfig();
        String convertedText = convert(inputText, config);
        if (cbPunctuation.isSelected()) {
            convertedText = convertPunctuation(convertedText, config);
        }
        textAreaDestination.setText(convertedText);
        if (!lblSourceCode.getText().contains("non") && !lblSourceCode.getText().isEmpty()) {
            lblDestinationCode.setText(lblSourceCode.getText().contains("Hans") ? "zh-Hant (繁体)" : "zh-Hans (简体)");
        } else {
            lblDestinationCode.setText(lblSourceCode.getText());
        }
        lblStatus.setText("Conversion process completed.");
    }

    private void startBatchConversion() {
        ObservableList<String> fileList = listViewSource.getItems();
        if (fileList.isEmpty()) {
            lblStatus.setText("Empty file list.\n");
            return;
        }

        String outputDirectory = textFieldPath.getText();
        Path outputDirectoryPath = Paths.get(outputDirectory);
        if (outputDirectory.isEmpty() || !Files.isDirectory(outputDirectoryPath)) {
            textAreaPreview.appendText(String.format("Output directory [ %s ] does not exist.\n", outputDirectory));
            return;
        }
        textAreaPreview.clear();
        final String config = getConfig();
        String convertedText;
        int counter = 0;
        for (String file : fileList) {
            counter++;
            if (!FILE_EXTENSIONS.contains(getFileExtension(file))) {
                textAreaPreview.appendText(String.format("%d : [Skipped] %s --> Not a valid file format.\n", counter, file));
            } else {
                File sourceFilePath = new File(file);
                String outputFilename = sourceFilePath.getName();
                Path outputFilePath = outputDirectoryPath.resolve(outputFilename);
                try {
                    String contents = Files.readString(new File(file).toPath());
                    convertedText = convert(contents, config);
                    if (cbPunctuation.isSelected()) {
                        convertedText = convertPunctuation(convertedText, config);
                    }
                    // Write string contents to the file with UTF-8 encoding
                    Files.writeString(outputFilePath, convertedText);
                    textAreaPreview.appendText(String.format("%d : %s --> [Done].\n", counter, outputFilePath));

                } catch (Exception e) {
//                    throw new RuntimeException(e);
                    textAreaPreview.appendText(String.format("%d : [Skipped] %s --> Error writing output file.\n", counter, outputFilePath));
                }
            }
        }
        textAreaPreview.appendText("Process completed.\n");
        lblStatus.setText("Batch conversion process completed.");
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
    }

    private String getConfig() {
        String config = "s2t";
        if (rbS2t.isSelected()) {
            config = rbStd.isSelected() ? "s2t" : (rbHK.isSelected() ? "s2hk" : (cbZHTW.isSelected() ? "s2twp" : "s2tw"));
        }
        if (rbT2s.isSelected()) {
            config = rbStd.isSelected() ? "t2s" : (rbHK.isSelected() ? "hk2s" : (cbZHTW.isSelected() ? "tw2sp" : "tw2s"));
        }
        return config;
    }

    public void onBtnOpenFileClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Text File");
        fileChooser.setInitialDirectory(new File("."));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("Subtitle Files", "*.srt;*.vtt;*.ass;*.xml;*.ttml2"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            if (selectedFile.isFile() && selectedFile.exists()) {
                displayFileContents(selectedFile);
            } else {
                lblStatus.setText("Selected file is not valid.");
            }
        }
    }

    private void displayFileContents(File file) {
        try {
            String content = Files.readString(file.toPath());
            textAreaSource.setText(content);
            updateSourceInfo(zhoCheck(content));
            lblFilename.setText(file.getName());
            lblStatus.setText(String.format("File: %s", file));
        } catch (IOException e) {
            lblStatus.setText("Error reading file: " + e.getMessage());
        }
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
                new FileChooser.ExtensionFilter("Subtitle Files", "*.srt;*.vtt;*.ass;*.xml;*.ttml2"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
        if (selectedFiles != null) {
            // Check for duplicate files and add only new files to the list
            int count = 0;
            for (File file : selectedFiles) {
                if (!currentFileList.contains(file.getAbsolutePath())) {
                    currentFileList.add(file.getAbsolutePath());
                    count++;
                }
            }
            FXCollections.sort(currentFileList, null);
            listViewSource.setItems(currentFileList);
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
            if (file.isFile() && FILE_EXTENSIONS.contains(fileExtension != null ? fileExtension.toLowerCase() : null)) {
                try {
                    String content = Files.readString(file.toPath());
                    textAreaPreview.setText(content);
                    lblStatus.setText(String.format("File Preview: %s", selectedItem));
                } catch (IOException e) {
                    textAreaSource.setText("Error reading file: " + e.getMessage());
                }
            } else {
                textAreaPreview.setText("Selected file is not a valid text file.");
            }
        }
    }

    public void onBtnSelecPathClicked() {
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
        lblStatus.setText("Preview cleared.");
    }

    public void onBtnSaveAsClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Text File");
        fileChooser.setInitialDirectory(new File("."));
        fileChooser.setInitialFileName("File.txt");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("Subtitle Files", "*.srt;*.vtt;*.ass;*.xml;*.ttml2"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showSaveDialog(null);

        if (selectedFile != null) {
            try {
                String contents = textAreaDestination.getText();
                // Write string contents to the file with UTF-8 encoding
                Files.writeString(selectedFile.toPath(), contents);
                lblStatus.setText(String.format("Output contents saved to: %s", selectedFile));
            } catch (Exception e) {
                lblStatus.setText(String.format("Error writing output file: %s", selectedFile));
            }
        }
    }

    public void onTaSourceDragOver(DragEvent dragEvent) {
        if (dragEvent.getGestureSource() != textAreaSource && dragEvent.getDragboard().hasFiles()) {
            dragEvent.acceptTransferModes(TransferMode.COPY);
        }
        dragEvent.consume();
    }

    public void onTaDragDropped(DragEvent dragEvent) {
        Dragboard dragboard = dragEvent.getDragboard();
        boolean success = false;
        if (dragboard.hasFiles()) {
            File file = dragboard.getFiles().get(0);
            if (isTextFile(file)) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    StringBuilder content = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    reader.close();
                    textAreaSource.setText(content.toString());
                    success = true;
                } catch (Exception e) {
                    lblStatus.setText("Error: " + e.getMessage());
                }
            } else {
                textAreaSource.setText("Not a valid text file.");
            }
        }
        dragEvent.setDropCompleted(success);
        dragEvent.consume();
    }

    private boolean isTextFile(File file) {
        String fileExtension = getFileExtension(file.getName());
        return file.isFile() && FILE_EXTENSIONS.contains(fileExtension != null ? fileExtension.toLowerCase() : null);
    }
} // class DemoFxController