@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem --- Config you might tweak ---
set "JAVA=C:\Java\zulu21.48.17-ca-fx-jdk21.0.10-win_x64\bin\java.exe"
set "LIB=lib"
set "RICH=c:\Java\richtextfx"
set "APP_DIR=build\libs"
rem ------------------------------

set "APP="

rem List newest-first; pick first that doesn't contain -sources/-javadoc/-tests
for /f "delims=" %%F in ('dir "%APP_DIR%\openccjavafx-*.jar" /b /a:-d /o:-d 2^>NUL') do (
  set "file=%%F"
  set "skip="
  for %%S in (-sources -javadoc -tests) do (
    if /I not "!file!"=="!file:%%S=!" set "skip=1"
  )
  if not defined skip (
    set "APP=%APP_DIR%\!file!"
    goto :got_app
  )
)

echo [ERROR] No app JAR found under "%APP_DIR%".
exit /b 1

:got_app
echo Using APP: "%APP%"

"%JAVA%" ^
  -Dfile.encoding=UTF-8 ^
  --module-path "%APP%;%LIB%;%RICH%" ^
  --add-modules org.fxmisc.richtext ^
  --module org.example.openccjavafx/org.example.openccjavafx.OpenccJavaFxApplication

endlocal
