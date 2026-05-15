@echo off
setlocal EnableDelayedExpansion
set "BASE_DIR=%~dp0"

if not defined JAVA_HOME goto detect_java_home
if not exist "%JAVA_HOME%\bin\java.exe" goto detect_java_home
goto run_maven

:detect_java_home
for /f "tokens=1,* delims==" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.home"') do set "JAVA_HOME=%%b"
for /f "tokens=* delims= " %%a in ("!JAVA_HOME!") do set "JAVA_HOME=%%a"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Unable to locate a valid JAVA_HOME.
  exit /b 1
)

:run_maven
mvn -f "%BASE_DIR%pom.xml" %*
