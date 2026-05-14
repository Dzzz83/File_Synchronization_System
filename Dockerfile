# Use a standard Eclipse Temurin (Adoptium) JDK 17 image as the base
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the entire source code (including the client folder, but we won't build it)
COPY . .

# Give execute permission to Maven Wrapper and build ONLY the server module
# -pl server : build only the server module
# -am : also build modules that the server depends on (common)
# -DskipTests : skip tests for faster build
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl server -am

# Expose the port your Spring Boot server runs on
EXPOSE 8080

# Set the entrypoint to run the built JAR file
ENTRYPOINT ["java", "-jar", "server/target/server-1.0-SNAPSHOT.jar"]