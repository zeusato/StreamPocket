@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat --no-daemon testDebugUnitTest assembleDebug lintDebug
set "BUILD_RESULT=%ERRORLEVEL%"
if "%BUILD_RESULT%"=="0" echo APK: %~dp0app\build\outputs\apk\debug\app-debug.apk
pause
exit /b %BUILD_RESULT%
