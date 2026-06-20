@echo off
setlocal
set GRADLE_VERSION=9.2.0
set GRADLE_DIR=%CD%\.gradle-local\gradle-%GRADLE_VERSION%
set GRADLE_ZIP=%CD%\.gradle-local\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VERSION%...
  if not exist "%CD%\.gradle-local" mkdir "%CD%\.gradle-local"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
  echo Extracting Gradle...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%GRADLE_ZIP%' '%CD%\.gradle-local'"
)
call "%GRADLE_DIR%\bin\gradle.bat" %*
