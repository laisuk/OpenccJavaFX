@echo off

set JAVA=java.exe
set LIB=lib
set APP=build/libs/openccjavafx-1.0.0.jar

:: Run the CLI (no JavaFX or module system required)
"%JAVA%" ^
  -Dfile.encoding=UTF-8 ^
  -cp "%APP%;%LIB%\*" ^
  openccjavacli.Main %*
