@echo off
REM Set JavaFX SDK lib path - update this if your path is different
set JAVAFX_LIB="C:\Users\HOME\Desktop\CODING\wastemanagement\javafx-sdk-24.0.2\lib"

REM Compile the Java source files with module support
javac --module-path %JAVAFX_LIB% -d out src\module-info.java src\app\WMRTApp.java

if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

REM Run the JavaFX application using module system
java --module-path %JAVAFX_LIB%;out -m app/app.WMRTApp

pause