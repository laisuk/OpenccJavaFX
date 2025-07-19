module org.example.openccfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.datatransfer;
    requires java.desktop;
    requires java.sql;
    requires org.fxmisc.richtext;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;

    opens org.example.openccfx to javafx.fxml;
    exports org.example.openccfx;
    exports openccjava;
}