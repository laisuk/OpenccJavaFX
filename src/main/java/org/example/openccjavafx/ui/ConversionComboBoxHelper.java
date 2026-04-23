package org.example.openccjavafx.ui;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import org.example.openccjavafx.ConfigItem;
import org.example.openccjavafx.SaveTargetItem;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class ConversionComboBoxHelper {
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

    private static final List<SaveTargetItem> SAVE_TARGET_LIST = Arrays.asList(
            new SaveTargetItem("destination"),
            new SaveTargetItem("source")
    );

    private ConversionComboBoxHelper() {
    }

    public static void setupManualCombo(ComboBox<ConfigItem> combo) {
        setupLocalizedCombo(combo, CONFIG_LIST, ConfigItem::labelProperty);
    }

    public static void setupSaveTargetCombo(ComboBox<SaveTargetItem> combo) {
        setupLocalizedCombo(combo, SAVE_TARGET_LIST, SaveTargetItem::labelProperty);
    }

    public static void refreshLabels() {
        for (ConfigItem item : CONFIG_LIST) {
            item.refreshLabel();
        }
        for (SaveTargetItem item : SAVE_TARGET_LIST) {
            item.refreshLabel();
        }
    }

    public static String getSelectedManualCode(ComboBox<ConfigItem> combo) {
        ConfigItem selected = combo.getValue();
        return selected != null ? selected.getCode() : null;
    }

    public static String getSelectedSaveTargetKey(ComboBox<SaveTargetItem> combo) {
        SaveTargetItem selected = combo.getValue();
        return selected != null ? selected.getKey() : null;
    }

    public static String getSelectedSaveTargetLabel(ComboBox<SaveTargetItem> combo) {
        SaveTargetItem selected = combo.getValue();
        return selected != null ? selected.labelProperty().get() : null;
    }

    private static <T> void setupLocalizedCombo(
            ComboBox<T> combo,
            List<T> items,
            Function<T, ObservableValue<String>> labelProperty
    ) {
        combo.getItems().setAll(items);
        combo.setCellFactory(listView -> createBoundLabelCell(labelProperty));
        combo.setButtonCell(createBoundLabelCell(labelProperty));
        combo.getSelectionModel().selectFirst();
    }

    private static <T> ListCell<T> createBoundLabelCell(Function<T, ObservableValue<String>> labelProperty) {
        return new ListCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                textProperty().unbind();

                if (empty || item == null) {
                    setText(null);
                } else {
                    textProperty().bind(labelProperty.apply(item));
                }
            }
        };
    }
}
