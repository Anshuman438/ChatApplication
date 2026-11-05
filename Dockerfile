# ==============================================================================
# Multi-stage Dockerfile for Render Deployment
# Builds Java backend + Desktop JAR + Nginx Web Client
# ==============================================================================

# Stage 1: Build JAR files with Maven
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY web ./web

RUN mvn clean package -DskipTests

# Stage 2: Production runtime with JRE 21 + Nginx
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y nginx gettext-base && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy built server and desktop JARs
COPY --from=builder /build/target/chat-server.jar /app/chat-server.jar
RUN mkdir -p /var/www/downloads
COPY --from=builder /build/target/ChatApp-Desktop.jar /var/www/downloads/ChatApp-Desktop.jar

# Copy static web application files
COPY web /var/www/html

# Copy Nginx template and entrypoint script
RUN mkdir -p /etc/nginx/templates
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Render sets PORT dynamically (defaults to 10000)
ENV PORT=10000
ENV WS_PORT=8081

EXPOSE 10000

ENTRYPOINT ["/app/entrypoint.sh"]
