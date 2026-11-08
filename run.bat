@echo off
set "JAVA_BIN=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe"
if not exist "%JAVA_BIN%" set "JAVA_BIN=java"

set "CP=%~dp0target\classes;%~dp0lib\*"

"%JAVA_BIN%" -Dfile.encoding=UTF-8 -cp "%CP%" com.btl.dbms.Main %*
