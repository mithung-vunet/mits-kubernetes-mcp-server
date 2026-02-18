@echo off
REM SSH Authentication Setup Launcher
REM Double-click this file or run from command prompt
REM
REM Usage:
REM   setup-ssh.bat
REM   setup-ssh.bat -Server "user@hostname"
REM   setup-ssh.bat -JumpServerUser myuser -JumpServerHost 192.168.1.100

echo.
echo ========================================
echo   Kubernetes MCP SSH Setup
echo ========================================
echo.
echo This script will configure passwordless SSH access to your jump server.
echo You will be prompted for the server details if not provided.
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0setup-ssh.ps1" %*
echo.
pause
