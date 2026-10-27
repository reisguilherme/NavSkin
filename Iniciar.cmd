@echo off
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Instale Node.js 22.12 ou superior e execute este arquivo novamente.
  pause
  exit /b 1
)
if not exist node_modules\vite\bin\vite.js (
  call npm ci --no-audit --no-fund
  if errorlevel 1 (
    echo Nao foi possivel instalar as dependencias. Verifique a conexao.
    pause
    exit /b 1
  )
)
call npm run dev -- --open
pause
