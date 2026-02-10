@echo off
echo Building and deploying AdminDashboard mod...

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

set DEST_DIR=C:\Users\PatrickJr\AppData\Roaming\Hytale\UserData\Mods\
set JAR_NAME=AdminWebDash-1.0.0.jar

if not exist "%DEST_DIR%" (
    echo Creating destination directory: %DEST_DIR%
    mkdir "%DEST_DIR%"
)

if not exist "target\%JAR_NAME%" (
    echo Error: target\%JAR_NAME% not found!
    
    exit /b 1
)

echo Copying %JAR_NAME% to %DEST_DIR%...
copy /Y "target\%JAR_NAME%" "%DEST_DIR%\" >> logs\build.log 2>&1

if %ERRORLEVEL% neq 0 (
    echo Copy failed! Check logs\build.log for details.
    type logs\build.log
    
    exit /b %ERRORLEVEL%
)

echo.
echo Mod successfully built and deployed to %DEST_DIR%
echo Build completed at: %date% %time% >> logs\build.log
echo ======================================== >> logs\build.log
echo. >> logs\build.log
