@echo off
setlocal EnableDelayedExpansion
title SOUIGAT Dev Launcher

echo.
echo  ============================================
echo   SOUIGAT Dev Launcher
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
    :: Extract port number from URL (last segment after the last colon)
    for /f "tokens=3 delims=:/" %%P in ("!RAW_URL!") do set "BACKEND_PORT=%%P"
    echo [CONFIG] Read from web\.env.local
    echo [CONFIG] Backend port : !BACKEND_PORT!
    echo [CONFIG] Frontend port: %FRONTEND_PORT%
) else (
    echo [WARN] web\.env.local not found — using defaults (BE:8002, FE:5173)
)

echo.

:: ── 2. Free backend port if occupied ───────────────────────────────────────
echo [PORT] Checking port !BACKEND_PORT!...
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":!BACKEND_PORT! " ^| findstr "LISTENING"') do (
    echo [PORT] Port !BACKEND_PORT! occupied by PID %%P — killing it...
    taskkill /PID %%P /F >nul 2>&1
    timeout /t 1 /nobreak >nul
)
echo [PORT] Port !BACKEND_PORT! is free.

:: ── 3. Free frontend port if occupied ──────────────────────────────────────
echo [PORT] Checking port %FRONTEND_PORT%...
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":%FRONTEND_PORT% " ^| findstr "LISTENING"') do (
    echo [PORT] Port %FRONTEND_PORT% occupied by PID %%P — killing it...
    taskkill /PID %%P /F >nul 2>&1
    timeout /t 1 /nobreak >nul
)
echo [PORT] Port %FRONTEND_PORT% is free.

echo.

:: ── 4. Start Backend ────────────────────────────────────────────────────────
echo [BE] Starting Django backend on port !BACKEND_PORT!...
start "SOUIGAT Backend :!BACKEND_PORT!" cmd /k "cd /d %~dp0backend && set DB_HOST=sqlite && set DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0 && .\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:!BACKEND_PORT!"

:: Give Django a moment to initialize before starting Vite
timeout /t 3 /nobreak >nul

:: ── 5. Start Frontend ───────────────────────────────────────────────────────
echo [FE] Starting Vite frontend on port %FRONTEND_PORT%...
start "SOUIGAT Frontend :%FRONTEND_PORT%" cmd /k "cd /d %~dp0web && npm run dev -- --host 0.0.0.0 --port %FRONTEND_PORT%"

echo.
echo  ============================================
echo   Both services started in separate windows.
echo   Frontend -> http://localhost:%FRONTEND_PORT%
echo   Backend  -> http://localhost:!BACKEND_PORT!
echo  ============================================
echo.
echo  To change the backend port: edit web\.env.local (API_PROXY_URL)
echo  then re-run this script.
echo.
pause
