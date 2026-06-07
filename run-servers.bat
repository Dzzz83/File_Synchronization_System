@echo off
set JAR_PATH=server\target\server-1.0-SNAPSHOT.jar
set PORTS=8080 8081 8082 8083

if not exist %JAR_PATH% (
    echo JAR not found at %JAR_PATH%. Run 'mvn clean package -DskipTests' first.
    exit /b 1
)

for %%p in (%PORTS%) do (
    start "Server on port %%p" cmd /c "java -jar %JAR_PATH% --server.port=%%p"
)

echo All 4 servers started. Close each window to stop the server.