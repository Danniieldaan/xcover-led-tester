@echo off
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
  echo gradle-wrapper.jar missing. Install Gradle or run: gradle wrapper
  echo Alternatively build in the cloud with the GitHub Actions workflow (.github/workflows/build-apk.yml^).
  exit /b 1
)
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -jar "%WRAPPER_JAR%" %*
