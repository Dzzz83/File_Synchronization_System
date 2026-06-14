@echo off
cd /d "%~dp0..\server"
start "Server-8080" java -jar target\server-1.0-SNAPSHOT.jar --server.port=8080 --server.address=0.0.0.0
timeout /t 2 /nobreak >nul
start "Server-8081" java -jar target\server-1.0-SNAPSHOT.jar --server.port=8081 --server.address=0.0.0.0
timeout /t 2 /nobreak >nul
start "Server-8082" java -jar target\server-1.0-SNAPSHOT.jar --server.port=8082 --server.address=0.0.0.0
timeout /t 2 /nobreak >nul
start "Server-8083" java -jar target\server-1.0-SNAPSHOT.jar --server.port=8083 --server.address=0.0.0.0
echo All server nodes started.