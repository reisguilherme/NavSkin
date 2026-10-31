@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0prototypes\navskin-map-lab\iniciar-paineis.ps1"
if errorlevel 1 pause
