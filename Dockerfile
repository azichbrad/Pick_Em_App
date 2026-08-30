# Stage 1: Build the Vaadin Production App
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src

# Run the Maven production build (this compiles the Java AND the Vaadin frontend)
RUN mvn clean package -Pproduction -DskipTests

# Stage 2: Run the App
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the compiled .jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the standard web port
EXPOSE 8080

# Start the Spring Boot server
ENTRYPOINT ["java", "-jar", "app.jar"]