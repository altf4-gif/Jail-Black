@echo off
cd /d "%~dp0"
echo ============================================
echo   Java 3D Engine - Manual Launch
echo ============================================
echo.

if not exist "pin\lwjgl-3.3.3.jar" (
    echo ERROR: Files not found in "pin" folder!
    echo Make sure you downloaded all 7 jar files into the "pin" folder.
    pause
    exit /b
)

set LIBS=pin\lwjgl-3.3.3.jar;pin\lwjgl-glfw-3.3.3.jar;pin\lwjgl-opengl-3.3.3.jar;pin\joml-1.10.5.jar
set NATIVES=pin\lwjgl-3.3.3-natives-windows.jar;pin\lwjgl-glfw-3.3.3-natives-windows.jar;pin\lwjgl-opengl-3.3.3-natives-windows.jar

echo [1/2] Compiling the game...
if not exist "out" mkdir out

javac -d out -cp "%LIBS%" src\main\java\game\*.java src\main\java\engine\*.java

if errorlevel 1 (
    echo.
    echo ============================================
    echo ERROR: Compilation failed. Check the text above.
    echo ============================================
    pause
    exit /b
)

echo Compilation successful.
echo.

echo [2/2] Launching the game...
java -cp "out;src\main\resources;%LIBS%;%NATIVES%" game.Main

echo.
echo ============================================
echo Game exited. If there was an error, it is above.
echo ============================================
pause
