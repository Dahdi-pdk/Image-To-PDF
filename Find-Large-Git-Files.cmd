@echo off
setlocal
cd /d "%~dp0"
title Cari File Besar untuk Git
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Find-Large-Git-Files.ps1"
if errorlevel 1 (
  echo.
  echo Skrip selesai dengan error. Periksa pesan di atas.
)
echo.
pause
endlocal
