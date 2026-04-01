@echo off
:: ApiViewer 실행 스크립트 (Windows)
:: 빌드 후 실행: run.bat
:: 빌드 없이 실행 (JAR 이미 있을 때): run.bat --no-build

set MVN=mvn
set JAR=target\api-viewer-1.0.0.jar

cd /d "%~dp0"

if "%1"=="--no-build" goto RUN

echo [INFO] 빌드 중...
"%MVN%" -q package -DskipTests
if errorlevel 1 (
    echo [ERROR] 빌드 실패
    pause
    exit /b 1
)

:RUN
if not exist "%JAR%" (
    echo [ERROR] JAR 파일이 없습니다. 먼저 빌드하세요.
    pause
    exit /b 1
)

echo [INFO] 서버 시작: http://localhost:8080
java -jar "%JAR%"
