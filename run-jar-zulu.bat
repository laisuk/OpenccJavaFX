@echo off

set JAVA=C:\Java\zulu21.44.17-ca-fx-jdk21.0.8-win_x64\bin\java.exe
set LIB=lib
set RICH=c:\Java\richtextfx
set APP=build/libs/openccjavafx-1.0.0.jar

:: Add the app jar to module path, along with dependencies
"%JAVA%" ^
  -Dfile.encoding=UTF-8 ^
  --module-path "%APP%;%LIB%;%RICH%" ^
  --add-modules org.fxmisc.richtext ^
  --module org.example.openccjavafx/org.example.openccjavafx.OpenccJavaFxApplication
