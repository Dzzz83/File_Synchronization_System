# Use a standard Eclipse Temurin (Adoptium) JDK 17 image as the base
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy Maven wrapper files first (these rarely change)
COPY mvnw .
COPY .mvn .mvn

# Copy only the parent and module pom.xml files (to cache dependencies)
COPY pom.xml .
COPY common/pom.xml common/
COPY server/pom.xml server/

# Create a dummy client directory to satisfy Maven module discovery
RUN mkdir -p client

# Give execute permission to Maven Wrapper and download dependencies
# (This layer will be cached unless a pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy the rest of the source code (including the real client folder)
COPY . .

# Build ONLY the server module (source change only recompiles, not redownloads deps)
RUN ./mvnw clean package -DskipTests -pl server -am

# Expose the port
EXPOSE 8080

# Run the JAR
ENTRYPOINT ["java", "-jar", "server/target/server-1.0-SNAPSHOT.jar"]