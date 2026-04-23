package org.example.openccjavafx;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.example.openccjavafx.i18n.I18n;

public final class SaveTargetItem {
    private final String key;
    private final StringProperty label = new SimpleStringProperty();

    public SaveTargetItem(String key) {
        this.key = key;
        refreshLabel();
    }

    public String getKey() {
        return key;
    }

    public StringProperty labelProperty() {
        return label;
    }

    public void refreshLabel() {
        switch (key) {
            case "source":
                label.set(I18n.get("saveTarget.source"));
                break;
            case "destination":
                label.set(I18n.get("saveTarget.destination"));
                break;
            default:
                label.set(key);
                break;
        }
    }
}