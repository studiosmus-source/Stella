@echo off
title Installazione avvio automatico

set STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set TARGET=%STARTUP%\MAAG QC Server.bat

echo @echo off > "%TARGET%"
echo start /min "MAAG QC" python "%~dp0server.py" >> "%TARGET%"

echo.
echo  Fatto! Il server si avviera' automaticamente ad ogni accensione del PC.
echo.
echo  Per disinstallare elimina il file:
echo  %TARGET%
echo.
pause
