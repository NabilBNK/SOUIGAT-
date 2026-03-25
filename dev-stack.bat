@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "BACKEND_DIR=%ROOT%\backend"
set "WEB_DIR=%ROOT%\web"
set "BACKEND_PORT=8002"
set "FRONTEND_PORT=5173"
set "BACKEND_LOG=%ROOT%\backend_live.log"
set "FRONTEND_LOG=%ROOT%\web_local.log"
set "FIREBASE_PROJECT_ID=souigat-6be49"
set "FIREBASE_CREDENTIAL=%ROOT%\souigat-6be49-firebase-adminsdk-fbsvc-d5ec3435dd.json"

if not "%~2"=="" set "BACKEND_PORT=%~2"
if not "%~3"=="" set "FRONTEND_PORT=%~3"

if /I "%~1"=="start" goto :start
if /I "%~1"=="stop" goto :stop
if /I "%~1"=="restart" goto :restart
if /I "%~1"=="status" goto :status

echo Usage: %~n0 ^<start^|stop^|restart^|status^> [backend_port] [frontend_port]
echo.
echo Examples:
echo   %~n0 start
echo   %~n0 start 8013 4003
echo   %~n0 stop
exit /b 1

:start
echo [SOUIGAT] Starting backend and frontend...

if not exist "%BACKEND_DIR%\.venv\Scripts\python.exe" (
    echo [ERROR] Backend virtualenv not found at "%BACKEND_DIR%\.venv\Scripts\python.exe"
    echo         Create it first: cd backend ^&^& python -m venv .venv
    exit /b 1
)

call :is_port_listening %BACKEND_PORT%
if %ERRORLEVEL% EQU 0 (
    echo [WARN] Backend port %BACKEND_PORT% is already in use. Skipping backend start.
) else (
    start "SOUIGAT Backend" cmd /c "cd /d ""%BACKEND_DIR%"" && set "DB_HOST=sqlite"&& set "DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0"&& set "FIREBASE_PROJECT_ID=%FIREBASE_PROJECT_ID%"&& set "FIREBASE_SERVICE_ACCOUNT_PATH=%FIREBASE_CREDENTIAL%"&& ""%BACKEND_DIR%\.venv\Scripts\python.exe"" manage.py runserver 0.0.0.0:%BACKEND_PORT% > ""%BACKEND_LOG%"" 2>&1"
    echo [OK] Backend starting on http://localhost:%BACKEND_PORT%/
)

call :is_port_listening %FRONTEND_PORT%
if %ERRORLEVEL% EQU 0 (
    echo [WARN] Frontend port %FRONTEND_PORT% is already in use. Skipping frontend start.
) else (
    start "SOUIGAT Frontend" cmd /c "cd /d ""%WEB_DIR%"" && set "API_PROXY_URL=http://localhost:%BACKEND_PORT%"&& npm run dev -- --host 0.0.0.0 --port %FRONTEND_PORT% --strictPort > ""%FRONTEND_LOG%"" 2>&1"
    echo [OK] Frontend starting on http://localhost:%FRONTEND_PORT%/
)

echo [INFO] Logs:
echo   - %BACKEND_LOG%
echo   - %FRONTEND_LOG%
exit /b 0

:stop
echo [SOUIGAT] Stopping backend and frontend...
call :kill_by_port %BACKEND_PORT% backend
call :kill_by_port %FRONTEND_PORT% frontend
exit /b 0

:restart
call :stop
ping -n 3 127.0.0.1 >nul
call :start
exit /b %ERRORLEVEL%

:status
call :print_status %BACKEND_PORT% backend
call :print_status %FRONTEND_PORT% frontend
exit /b 0

:is_port_listening
netstat -ano | findstr /R /C:":%~1 .*LISTENING" >nul
if %ERRORLEVEL% EQU 0 (
    exit /b 0
)
exit /b 1

:kill_by_port
set "PORT=%~1"
set "NAME=%~2"
set "FOUND="

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
    set "FOUND=1"
    taskkill /PID %%P /T /F >nul 2>&1
)

if defined FOUND (
    echo [OK] Stopped %NAME% on port %PORT%.
) else (
    echo [INFO] No %NAME% process found on port %PORT%.
)
exit /b 0

:print_status
set "PORT=%~1"
set "NAME=%~2"

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
    echo [RUNNING] %NAME% on port %PORT% ^(PID %%P^)
    exit /b 0
)

echo [STOPPED] %NAME% on port %PORT%
exit /b 0
