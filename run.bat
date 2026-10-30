@echo off
cd /d "%~dp0"
echo ============================================
echo   Java 3D Engine - Map Editor
echo ============================================
echo.

if not exist "..\JBfly" mkdir "..\JBfly"

if not exist "..\JBfly\lwjgl-3.3.6.jar" (
    echo Downloading LWJGL libraries...
    curl -L -o ..\JBfly\lwjgl-3.3.6.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6.jar
    curl -L -o ..\JBfly\lwjgl-glfw-3.3.6.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/3.3.6/lwjgl-glfw-3.3.6.jar
    curl -L -o ..\JBfly\lwjgl-opengl-3.3.6.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/3.3.6/lwjgl-opengl-3.3.6.jar
    curl -L -o ..\JBfly\joml-1.10.7.jar https://repo1.maven.org/maven2/org/joml/joml/1.10.7/joml-1.10.7.jar
    curl -L -o ..\JBfly\lwjgl-3.3.6-natives-windows.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6-natives-windows.jar
    curl -L -o ..\JBfly\lwjgl-glfw-3.3.6-natives-windows.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/3.3.6/lwjgl-glfw-3.3.6-natives-windows.jar
    curl -L -o ..\JBfly\lwjgl-opengl-3.3.6-natives-windows.jar https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/3.3.6/lwjgl-opengl-3.3.6-natives-windows.jar
)

if not exist "..\JBfly\imgui-java-binding-1.86.11.jar" (
    echo Downloading ImGui libraries...
    curl -L -o ..\JBfly\imgui-java-binding-1.86.11.jar https://repo1.maven.org/maven2/io/github/spair/imgui-java-binding/1.86.11/imgui-java-binding-1.86.11.jar
    curl -L -o ..\JBfly\imgui-java-lwjgl3-1.86.11.jar https://repo1.maven.org/maven2/io/github/spair/imgui-java-lwjgl3/1.86.11/imgui-java-lwjgl3-1.86.11.jar
    curl -L -o ..\JBfly\imgui-java-natives-windows-1.86.11.jar https://repo1.maven.org/maven2/io/github/spair/imgui-java-natives-windows/1.86.11/imgui-java-natives-windows-1.86.11.jar
    curl -L -o ..\JBfly\imgui-java-app-1.86.11.jar https://repo1.maven.org/maven2/io/github/spair/imgui-java-app/1.86.11/imgui-java-app-1.86.11.jar
)

set LIBS=..\JBfly\lwjgl-3.3.6.jar;..\JBfly\lwjgl-glfw-3.3.6.jar;..\JBfly\lwjgl-opengl-3.3.6.jar;..\JBfly\joml-1.10.7.jar
set LIBS=%LIBS%;..\JBfly\imgui-java-binding-1.86.11.jar;..\JBfly\imgui-java-lwjgl3-1.86.11.jar;..\JBfly\imgui-java-natives-windows-1.86.11.jar;..\JBfly\imgui-java-app-1.86.11.jar
set NATIVES=..\JBfly\lwjgl-3.3.6-natives-windows.jar;..\JBfly\lwjgl-glfw-3.3.6-natives-windows.jar;..\JBfly\lwjgl-opengl-3.3.6-natives-windows.jar

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
java --enable-native-access=ALL-UNNAMED -cp "out;src\main\resources;%LIBS%;%NATIVES%" game.Main

echo.
echo ============================================
echo Game exited. If there was an error, it is above.
echo ============================================
pause
