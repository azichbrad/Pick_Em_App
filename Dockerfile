# Single Stage: Both Build and Run
FROM maven:3.9-eclipse-temurin-21
WORKDIR /app

# Install Node.js so Vaadin can serve the UI exactly like it does locally
RUN apt-get update && apt-get install -y curl \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs

# Copy your code
COPY pom.xml .
COPY src ./src

# Build the standard app (NO production flag)
RUN mvn clean package -DskipTests

# Run the app with Render's dynamic port AND force IPv4 networking
ENTRYPOINT sh -c "java -Dserver.port=${PORT:-8080} -Djava.net.preferIPv4Stack=true -jar target/*.jar"