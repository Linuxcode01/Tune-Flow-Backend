@echo off
REM ---------------------------------------------------------------------------------------------
REM  TuneFlow backend - start on Windows with the zero-dependency dev profile.
REM
REM  H2 in-memory database, Caffeine cache, local disk storage, in-process events, seeded demo
REM  data. No PostgreSQL, Redis, Kafka or MinIO required.
REM
REM  Needs a JDK only - Maven is downloaded by the bundled wrapper on first use. If JAVA_HOME is
REM  not already set, this script falls back to the JDK that ships inside Android Studio.
REM
REM  Media URLs are signed against TUNEFLOW_PUBLIC_BASE_URL. If you do not set it yourself, this
REM  script detects the PC's Wi-Fi/LAN IPv4 address and signs against it, so a phone on the same
REM  network can play songs directly.
REM
REM  Usage:  double-click, or from a terminal:  run-dev.cmd
REM ---------------------------------------------------------------------------------------------
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
        echo [tuneflow] JAVA_HOME not set - using the Android Studio JDK at "!JAVA_HOME!"
    ) else (
        echo [tuneflow] JAVA_HOME is not set and no Android Studio JDK was found.
        echo [tuneflow] Install a JDK 21 and run:  set "JAVA_HOME=C:\path\to\jdk-21"
        exit /b 1
    )
)

REM --------------------------------------------------------------------------------------------
REM Auto-detect the LAN IP (first 192.168.x / 10.x / 172.x address) for signing media URLs.
REM Phones on the same Wi-Fi cannot reach "localhost" - that is the PC itself. Set
REM TUNEFLOW_PUBLIC_BASE_URL yourself to override this detection.
REM --------------------------------------------------------------------------------------------
if not defined TUNEFLOW_PUBLIC_BASE_URL (
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
        for /f "tokens=1" %%b in ("%%a") do (
            if not defined _LANIP (
                echo %%b | findstr /r "^192\.168\. ^10\. ^172\." >nul && set "_LANIP=%%b"
            )
        )
    )
    if defined _LANIP (
        set "TUNEFLOW_PUBLIC_BASE_URL=http://!_LANIP!:8080"
        echo [tuneflow] Detected LAN IP !_LANIP! - media URLs will be signed for http://!_LANIP!:8080
    ) else (
        echo [tuneflow] No LAN IP detected - media URLs will be signed for http://localhost:8080
    )
)

if not exist "target\tuneflow-backend-1.0.0.jar" (
    echo [tuneflow] First run - building the jar ^(this downloads dependencies once^)...
    call mvnw.cmd -B -q -DskipTests package
    if errorlevel 1 (
        echo [tuneflow] Build failed. See the output above.
        exit /b 1
    )
)

echo [tuneflow] Starting the API on http://localhost:8080  ^(Swagger: /swagger-ui/index.html^)
echo [tuneflow] Demo login: listener@tuneflow.dev / Password123
echo [tuneflow] Press Ctrl+C to stop.
echo.
"%JAVA_HOME%\bin\java" -jar "target\tuneflow-backend-1.0.0.jar" --spring.profiles.active=dev

endlocal
