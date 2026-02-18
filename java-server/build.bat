@echo off
REM Build script for Kubernetes MCP Server (Java) - Windows
setlocal enabledelayedexpansion

echo Building Kubernetes MCP Server (Java)...
cd /d "%~dp0"

REM Check Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: Java not found. Install JDK 17 or higher.
    exit /b 1
)

REM Check Maven
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: Maven not found. Install Maven 3.8+
    exit /b 1
)

REM Build
echo Compiling...
call mvn clean package -DskipTests -q

if %errorlevel% neq 0 (
    echo Build failed!
    exit /b 1
)

echo.
echo Build complete!
echo.
echo Output: target\kubernetes-mcp-server-1.0.0.jar
echo.
echo Deploy to jump server:
echo   scp target\kubernetes-mcp-server-1.0.0.jar user@jump-server:~/
echo.
echo Test locally:
echo   java -jar target\kubernetes-mcp-server-1.0.0.jar
