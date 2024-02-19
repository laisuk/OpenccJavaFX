@echo off
java -jar "-Dfile.encoding=UTF-8" --module-path "lib;C:\Java\javafx-sdk-21.0.2\lib" --add-modules javafx.controls,javafx.fxml,OpenCC.Java  build/libs/demofx-1.0-SNAPSHOT.jar