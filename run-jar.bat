@echo off

set JAVA=java.exe
set JFX=C:\Java\javafx-sdk-23.0.2\lib
set LIB=lib
set RICH=c:\Java\richtextfx
set APP=build/libs/openccjavafx-1.0-SNAPSHOT.jar

:: Add the app jar to module path, along with dependencies
"%JAVA%" ^
  -Dfile.encoding=UTF-8 ^
  --module-path "%APP%;%JFX%;%LIB%;%RICH%" ^
  --add-modules javafx.controls,javafx.fxml,org.fxmisc.richtext ^
  --module org.example.openccjavafx/org.example.openccjavafx.OpenccJavaFxApplication
