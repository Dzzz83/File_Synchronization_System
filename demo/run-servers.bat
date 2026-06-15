@echo off
cd /d "%~dp0.."

echo Stopping any existing server instances...
for /L %%i in (8080,1,8083) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%%i') do (
        taskkill /F /PID %%a 2>nul
    )
)
timeout /t 2 /nobreak >nul

echo Starting Server-8080...
start "Server-8080" cmd /k "java -Djavamelody.storage-directory=./javamelody-8080 -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8080 --server.address=0.0.0.0"
timeout /t 2 /nobreak >nul

echo Starting Server-8081...
start "Server-8081" cmd /k "java -Djavamelody.storage-directory=./javamelody-8081 -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8081 --server.address=0.0.0.0"
timeout /t 2 /nobreak >nul

echo Starting Server-8082...
start "Server-8082" cmd /k "java -Djavamelody.storage-directory=./javamelody-8082 -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8082 --server.address=0.0.0.0"
timeout /t 2 /nobreak >nul

echo Starting Server-8083...
start "Server-8083" cmd /k "java -Djavamelody.storage-directory=./javamelody-8083 -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8083 --server.address=0.0.0.0"

echo All server nodes started.