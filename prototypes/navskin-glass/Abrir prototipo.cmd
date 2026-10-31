@echo off
cd /d "%~dp0..\.."
echo Abra http://127.0.0.1:4175/ no navegador.
echo Para encerrar o servidor, pressione Ctrl+C.
node prototypes/navskin-glass/server.mjs
pause
