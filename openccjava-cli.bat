@echo off
REM Batch file to run openccjavacli for distZip
REM Save current directory
pushd "%~dp0\.."

REM Run the Java CLI with classpath to lib/*
java -cp lib/* openccjavacli.Main %*

REM Restore original directory
popd
