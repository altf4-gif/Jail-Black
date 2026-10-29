@echo off
cd /d "%~dp0"
echo ============================================
echo   Java 3D Engine - Direct Compilation
echo ============================================
echo.

if not exist "..\JBfly" mkdir "..\JBfly"

if not exist "..\JBfly\lwjgl-3.3.3.jar" (
    echo Downloading LWJGL libraries to parallel JBfly folder...
    curl -L -o ..\JBfly\lwjgl-3.3.3.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar
    curl -L -o ..\JBfly\lwjgl-glfw-3.3.3.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3.jar
    curl -L -o ..\JBfly\lwjgl-opengl-3.3.3.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/3.3.3/lwjgl-opengl-3.3.3.jar
    curl -L -o ..\JBfly\joml-1.10.5.jar https://repo1.maven.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar
    curl -L -o ..\JBfly\lwjgl-3.3.3-natives-windows.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar
    curl -L -o ..\JBfly\lwjgl-glfw-3.3.3-natives-windows.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3-natives-windows.jar
    curl -L -o ..\JBfly\lwjgl-opengl-3.3.3-natives-windows.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/3.3.3/lwjgl-opengl-3.3.3-natives-windows.jar
)

set LIBS=..\JBfly\lwjgl-3.3.3.jar;..\JBfly\lwjgl-glfw-3.3.3.jar;..\JBfly\lwjgl-opengl-3.3.3.jar;..\JBfly\joml-1.10.5.jar
set NATIVES=..\JBfly\lwjgl-3.3.3-natives-windows.jar;..\JBfly\lwjgl-glfw-3.3.3-natives-windows.jar;..\JBfly\lwjgl-opengl-3.3.3-natives-windows.jar

echo [1/2] Compiling...
if not exist "out" mkdir out
javac -d out -cp "%LIBS%" src\main\java\game\*.java src\main\java\engine\*.java

if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed. Check the text above.
    pause
    exit /b
)

echo Compilation successful.
echo.
echo [2/2] Launching game...
java -cp "out;src\main\resources;%LIBS%;%NATIVES%" game.Main

echo.
echo ============================================
echo Game exited. If there was an error, it is above.
echo ============================================
pause
