# Use a standard Eclipse Temurin (Adoptium) JDK 17 image as the base
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven Wrapper and the project's POM files first (for better layer caching)
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
COPY common/pom.xml common/
COPY server/pom.xml server/

# Give the Maven Wrapper execute permissions and download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline

# Copy the rest of the source code and build the server module
COPY . .
RUN ./mvnw clean package -DskipTests

# Expose the port your Spring Boot server runs on
EXPOSE 8080

# Set the entrypoint to run the built JAR file
ENTRYPOINT ["java", "-jar", "server/target/server-1.0-SNAPSHOT.jar"]