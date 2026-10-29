@echo off
cd /d "%~dp0"
echo ============================================
echo   Game Folder Structure (Jail-Black)
echo ============================================
tree /F /A
echo.
echo ============================================
echo   Parallel Folder (JBfly)
echo ============================================
if exist "..\JBfly" (
    dir /A /B "..\JBfly"
) else (
    echo (JBfly not found - run run.bat first)
)
echo ============================================
pause
