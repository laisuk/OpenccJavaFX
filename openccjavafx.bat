@echo off

set JAVA=java.exe
set JFX=C:\Java\javafx-sdk-21.0.10\lib
set LIB=lib
set RICH=c:\Java\richtextfx
set APP=build/libs/openccjavafx-1.2.0.jar

:: Add the app jar to module path, along with dependencies
"%JAVA%" ^
  -Dfile.encoding=UTF-8 ^
  --enable-native-access=javafx.graphics ^
  --module-path "%APP%;%JFX%;%LIB%;%RICH%" ^
  --add-modules javafx.controls,javafx.fxml,org.fxmisc.richtext ^
  --module org.example.openccjavafx/org.example.openccjavafx.OpenccJavaFxApplication
