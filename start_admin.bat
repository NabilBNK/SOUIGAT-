@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "BACKEND_DIR=%ROOT%\backend"
set "WEB_DIR=%ROOT%\web"
set "BACKEND_PORT=8013"
set "FRONTEND_PORT=5173"
set "BACKEND_URL=http://127.0.0.1:%BACKEND_PORT%"
set "BACKEND_LOG=%BACKEND_DIR%\runserver.live.out.log"
set "FRONTEND_LOG=%WEB_DIR%\vite.live.out.log"

if not "%~1"=="" set "BACKEND_PORT=%~1"
if not "%~2"=="" set "FRONTEND_PORT=%~2"
set "BACKEND_URL=http://127.0.0.1:%BACKEND_PORT%"

echo [SOUIGAT] Admin start script
echo [INFO] Backend target:  %BACKEND_URL%
echo [INFO] Frontend target: http://127.0.0.1:%FRONTEND_PORT%
echo.

if not exist "%BACKEND_DIR%\manage.py" (
  echo [ERROR] backend\manage.py not found.
  exit /b 1
)

if not exist "%BACKEND_DIR%\.venv\Scripts\python.exe" (
  echo [ERROR] Backend virtualenv python is missing: "%BACKEND_DIR%\.venv\Scripts\python.exe"
  exit /b 1
)

call :ensure_proxy_env
if errorlevel 1 exit /b 1

call :get_listen_pid %BACKEND_PORT%
set "BACKEND_PID=%LISTEN_PID%"
if defined BACKEND_PID (
  echo [OK] Backend port %BACKEND_PORT% already in use by PID %BACKEND_PID%
) else (
  echo [INFO] Backend port %BACKEND_PORT% is free. Starting backend...
  start "SOUIGAT Backend" cmd /c "cd /d ""%BACKEND_DIR%"" && set DB_HOST=sqlite&& set DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0&& ""%BACKEND_DIR%\.venv\Scripts\python.exe"" manage.py runserver 0.0.0.0:%BACKEND_PORT% --noreload > ""%BACKEND_LOG%"" 2>&1"
  call :wait_for_port %BACKEND_PORT% 15
  call :get_listen_pid %BACKEND_PORT%
  if not defined LISTEN_PID (
    echo [ERROR] Backend failed to start on port %BACKEND_PORT%.
    echo [INFO] Check log: %BACKEND_LOG%
    exit /b 1
  )
  echo [OK] Backend started on port %BACKEND_PORT% with PID %LISTEN_PID%
)

call :get_listen_pid %FRONTEND_PORT%
set "FRONTEND_PID=%LISTEN_PID%"
if defined FRONTEND_PID (
  echo [OK] Frontend port %FRONTEND_PORT% already in use by PID %FRONTEND_PID%
) else (
  echo [INFO] Frontend port %FRONTEND_PORT% is free. Starting frontend...
  start "SOUIGAT Frontend" cmd /c "cd /d ""%WEB_DIR%"" && set API_PROXY_URL=%BACKEND_URL%&& npm run dev -- --host 0.0.0.0 --port %FRONTEND_PORT% --strictPort > ""%FRONTEND_LOG%"" 2>&1"
  call :wait_for_port %FRONTEND_PORT% 20
  call :get_listen_pid %FRONTEND_PORT%
  if not defined LISTEN_PID (
    echo [ERROR] Frontend failed to start on port %FRONTEND_PORT%.
    echo [INFO] Check log: %FRONTEND_LOG%
    exit /b 1
  )
  echo [OK] Frontend started on port %FRONTEND_PORT% with PID %LISTEN_PID%
)

echo.
echo [DONE] Project is ready.
echo [URL] Frontend: http://127.0.0.1:%FRONTEND_PORT%
echo [URL] Backend:  %BACKEND_URL%
echo [LOG] Backend:  %BACKEND_LOG%
echo [LOG] Frontend: %FRONTEND_LOG%
exit /b 0

:ensure_proxy_env
setlocal
set "ENV_PATH=%WEB_DIR%\.env.local"
set "PS_CMD=$path='%ENV_PATH%'; $target='API_PROXY_URL=%BACKEND_URL%'; if(Test-Path $path){$lines=Get-Content $path; $changed=$false; $found=$false; $out=@(); foreach($line in $lines){ if($line -match '^API_PROXY_URL='){ $found=$true; if($line -ne $target){$changed=$true}; $out += $target } else { $out += $line } }; if(-not $found){ $out=@($target)+$out; $changed=$true }; if($changed){ Set-Content -Path $path -Value $out -Encoding UTF8; 'CHANGED' } else { 'UNCHANGED' }} else { Set-Content -Path $path -Value $target -Encoding UTF8; 'CHANGED' }"
for /f %%R in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "%PS_CMD%"') do set "UPDATE_RESULT=%%R"
if /I "%UPDATE_RESULT%"=="CHANGED" (
  echo [OK] Updated web\.env.local to API_PROXY_URL=%BACKEND_URL%
) else if /I "%UPDATE_RESULT%"=="UNCHANGED" (
  echo [OK] web\.env.local already points to %BACKEND_URL%
) else (
  echo [ERROR] Failed to validate/update web\.env.local
  exit /b 1
)
endlocal & exit /b 0

:get_listen_pid
set "LISTEN_PID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%~1 .*LISTENING"') do (
  set "LISTEN_PID=%%P"
  goto :eof
)
goto :eof

:wait_for_port
setlocal
set "TARGET_PORT=%~1"
set "MAX_TRIES=%~2"
if "%MAX_TRIES%"=="" set "MAX_TRIES=10"
set /a TRY=0
:wait_loop
set /a TRY+=1
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%TARGET_PORT% .*LISTENING"') do (
  endlocal & exit /b 0
)
if %TRY% GEQ %MAX_TRIES% (
  endlocal & exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto :wait_loop
