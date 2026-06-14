@echo off
cd /d "%~dp0.."

echo Starting Server-8080...
start "Server-8080" java -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8080 --server.address=0.0.0.0
timeout /t 2 /nobreak >nul

echo Starting Server-8081...
start "Server-8081" java -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8081 --server.address=0.0.0.0
timeout /t 2 /nobreak >nul

echo Starting Server-8082...
start "Server-8082" java -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8082 --server.address=0.0.0.0
timeout /t 2 /nobreak >nul

echo Starting Server-8083...
start "Server-8083" java -jar server\target\server-1.0-SNAPSHOT.jar --server.port=8083 --server.address=0.0.0.0

echo All server nodes started.