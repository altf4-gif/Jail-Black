@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo   Integrity check: Java + shaders
echo ============================================
echo.

set OK=0
set FAIL=0

for %%f in (
  src\main\java\com\jailblack\Main.java
  src\main\java\com\jailblack\engine\Mesh.java
  src\main\java\com\jailblack\engine\MeshFactory.java
  src\main\java\com\jailblack\editor\EditorState.java
  src\main\resources\shaders\basic.vert
  src\main\resources\shaders\basic.frag
  src\main\resources\shaders\grid.vert
  src\main\resources\shaders\grid.frag
  src\main\resources\shaders\outline.vert
  src\main\resources\shaders\outline.frag
  src\main\resources\shaders\ghost.vert
  src\main\resources\shaders\ghost.frag
  src\main\resources\shaders\post.vert
  src\main\resources\shaders\post.frag
) do (
  echo === %%f ===
  if not exist "%%f" (
    echo   MISSING FILE
    set /a FAIL+=1
  ) else (
    for /f "usebackq delims=" %%L in (`powershell -NoProfile -Command "Get-Content -LiteralPath '%%f' -Tail 1"`) do (
      if "%%L"=="// === END OF FILE ===" (
        echo   OK
        set /a OK+=1
      ) else (
        echo   TRUNCATED / WRONG MARKER: %%L
        set /a FAIL+=1
      )
    )
  )
)

echo.
echo ============================================
echo OK: %OK%   FAIL: %FAIL%
if %FAIL% GTR 0 echo Some files are broken - re-extract the ZIP!
echo ============================================
pause
