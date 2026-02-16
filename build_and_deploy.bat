@echo off
echo Building and deploying AdminWebDash mod...

REM Create logs directory if it doesn't exist
if not exist "logs" (
    echo Creating logs directory...
    mkdir logs
)

echo Packaging mod...
call mvn clean package -Dmaven.test.skip=true > logs\build.log 2>&1
if %ERRORLEVEL% neq 0 (
    echo Maven package failed! Check logs\build.log for details.
    type logs\build.log
    exit /b %ERRORLEVEL%
)

set JAR_NAME=AdminWebDash-1.0.1.jar
set DEST_DIR1=C:\Users\PatrickJr\AppData\Roaming\Hytale\UserData\Mods\
set DEST_DIR2=F:\HyServerDocker\mods\

if not exist "target\%JAR_NAME%" (
    echo Error: target\%JAR_NAME% not found!
    exit /b 1
)

REM Ensure destination directories exist
if not exist "%DEST_DIR1%" mkdir "%DEST_DIR1%"
if not exist "%DEST_DIR2%" mkdir "%DEST_DIR2%"

echo Copying %JAR_NAME% to %DEST_DIR1%...
copy /Y "target\%JAR_NAME%" "%DEST_DIR1%" >> logs\build.log 2>&1
if %ERRORLEVEL% neq 0 (
    echo Copy to %DEST_DIR1% failed!
    type logs\build.log
    exit /b %ERRORLEVEL%
)

echo Copying %JAR_NAME% to %DEST_DIR2%...
copy /Y "target\%JAR_NAME%" "%DEST_DIR2%" >> logs\build.log 2>&1
if %ERRORLEVEL% neq 0 (
    echo Copy to %DEST_DIR2% failed!
    type logs\build.log
    exit /b %ERRORLEVEL%
)

echo.
echo Mod successfully built and deployed to:
echo  - %DEST_DIR1%
echo  - %DEST_DIR2%

echo Build completed at: %date% %time% >> logs\build.log
echo ======================================== >> logs\build.log
echo. >> logs\build.log
