@echo off
echo ClipTV Mod Builder
echo ==================
echo.
echo Checking Java...
java -version 2>nul
if errorlevel 1 (
    echo ERROR: Java not found. Install JDK 21 from https://adoptium.net
    pause
    exit /b 1
)
echo.
echo Building Fabric jar...
call gradlew.bat :fabric:remapJar --no-daemon
echo.
echo Building NeoForge jar...
call gradlew.bat :neoforge:remapJar --no-daemon
echo.
echo Done! Your jars are in:
echo   fabric\build\libs\clapcraft-cliptv-fabric-1.0.0.jar
echo   neoforge\build\libs\clapcraft-cliptv-neoforge-1.0.0.jar
pause
