@echo off

set JAVA=C:\Java\zulu21.42.19-ca-fx-jdk21.0.7-win_x64\bin\java.exe
set LIB=lib
set RICH=c:\Java\richtextfx
set APP=build/libs/demofx-1.0-SNAPSHOT.jar

:: Add the app jar to module path, along with dependencies
"%JAVA%" ^
  -Dfile.encoding=UTF-8 ^
  --module-path "%APP%;%LIB%;%RICH%" ^
  --add-modules org.fxmisc.richtext ^
  --module org.example.demofx/org.example.demofx.DemoFxApplication
