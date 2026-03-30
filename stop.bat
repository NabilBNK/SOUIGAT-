@echo off
setlocal EnableDelayedExpansion
title SOUIGAT Dev Stopper

echo.
echo  ============================================
echo   SOUIGAT Dev Stopper
echo  ============================================
echo.

:: ── 1. Read API_PROXY_URL from web/.env.local ──────────────────────────────
set "BACKEND_PORT=8002"
set "FRONTEND_PORT=5173"
set "ENV_FILE=%~dp0web\.env.local"

if exist "%ENV_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
        if "%%A"=="API_PROXY_URL" (
            set "RAW_URL=%%B"
        )
    )
    for /f "tokens=3 delims=:/" %%P in ("!RAW_URL!") do set "BACKEND_PORT=%%P"
)

echo [STOP] Targeting backend port  : !BACKEND_PORT!
echo [STOP] Targeting frontend port : %FRONTEND_PORT%
echo.

:: ── 2. Kill backend port ────────────────────────────────────────────────────
set "KILLED_BE=0"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":!BACKEND_PORT! " ^| findstr "LISTENING"') do (
    echo [BE] Killing PID %%P on port !BACKEND_PORT!...
    taskkill /PID %%P /F >nul 2>&1
    set "KILLED_BE=1"
)
if "!KILLED_BE!"=="0" echo [BE] Nothing running on port !BACKEND_PORT!.

:: ── 3. Kill frontend port ───────────────────────────────────────────────────
set "KILLED_FE=0"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":%FRONTEND_PORT% " ^| findstr "LISTENING"') do (
    echo [FE] Killing PID %%P on port %FRONTEND_PORT%...
    taskkill /PID %%P /F >nul 2>&1
    set "KILLED_FE=1"
)
if "!KILLED_FE!"=="0" echo [FE] Nothing running on port %FRONTEND_PORT%.

:: ── 4. Close the titled windows opened by start.bat ────────────────────────
taskkill /FI "WINDOWTITLE eq SOUIGAT Backend*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq SOUIGAT Frontend*" /F >nul 2>&1

echo.
echo  ============================================
echo   All SOUIGAT services stopped.
echo  ============================================
echo.
pause
