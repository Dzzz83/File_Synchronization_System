FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY . .

RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl server -am

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server/target/server-1.0-SNAPSHOT.jar"]