@echo off
echo ========================================
echo Waste Management & Route Tracker
echo ========================================
echo.
echo Compiling JavaFX application...
javac --module-path "javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web -d out src\module-info.java src\app\WMRTApp.java

if errorlevel 1 (
    echo.
    echo [ERROR] Compilation failed!
    echo Please check the error messages above.
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo.
echo Starting application with interactive map...
echo - Borderless fullscreen mode
echo - Real Bacolod City barangay coordinates
echo - 5 collection centers with status indicators
echo.
java --module-path "javafx-sdk-24.0.2\lib;out" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web -m app/app.WMRTApp
