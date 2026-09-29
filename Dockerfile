# 1. The dashboard (React) - built separately so the Maven image needs no Node
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm test && npm run build

# 2. The app, with the dashboard bundled into static/
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=frontend /frontend/dist ./frontend/dist
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
# Timestamps (orders, "today" in analytics) are the shop's local time
ENV TZ=Asia/Ulaanbaatar
# Size the heap from the container's memory limit rather than the host's
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
