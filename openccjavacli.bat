@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem === Resolve script directory (absolute, with trailing backslash) ===
set "SCRIPT_DIR=%~dp0"

rem === Java: prefer JAVA_HOME if set ===
set "JAVA=java.exe"
if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"

rem === Lib folder (optional). Override with OPENCC_LIB if you want. ===
set "LIB=%SCRIPT_DIR%lib"
if defined OPENCC_LIB set "LIB=%OPENCC_LIB%"

rem === JAR: allow override, else pick newest openccjavafx-*.jar under build\libs ===
if defined OPENCC_JAR (
  set "APP=%OPENCC_JAR%"
) else (
  set "APP="
  for /f "delims=" %%F in ('dir /b /o:-d "%SCRIPT_DIR%build\libs\openccjavafx-*.jar" 2^>nul') do (
    set "APP=%SCRIPT_DIR%build\libs\%%F"
    goto :gotjar
  )
)

:gotjar
if not defined APP (
  echo [ERROR] No JAR found under "%SCRIPT_DIR%build\libs" matching openccjavafx-*.jar
  echo         Build the project or set OPENCC_JAR to a specific file.
  exit /b 1
)

if not exist "%APP%" (
  echo [ERROR] JAR not found: "%APP%"
  exit /b 1
)

rem === Classpath: jar + optional libs ===
set "CP=%APP%"
if exist "%LIB%" (
  set "CP=%APP%;%LIB%\*"
)

rem === Extra JVM options (optional): set OPENCC_OPTS=-Xms256m -Xmx1g etc. ===
if not defined OPENCC_OPTS set "OPENCC_OPTS="

rem === Debug hint ===
if defined OPENCC_CLI_DEBUG (
  echo JAVA="%JAVA%"
  echo APP ="%APP%"
  echo LIB ="%LIB%"
  echo CP  ="%CP%"
)

rem === Run the CLI (Main class approach; no JavaFX/modules needed) ===
"%JAVA%" %OPENCC_OPTS% ^
  -Dfile.encoding=UTF-8 ^
  -cp "%CP%" ^
  openccjavacli.Main %*

endlocal
