module org.example.demofx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.datatransfer;
    requires java.desktop;
    requires java.sql;
    requires org.fxmisc.richtext;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;

    opens org.example.demofx to javafx.fxml;
    exports org.example.demofx;
    exports openccjava;
}