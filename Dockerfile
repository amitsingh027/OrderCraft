FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci --install-strategy=shallow
COPY frontend/ ./
RUN npm run build
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/ ./
COPY --from=frontend /app/dist/browser/ src/main/resources/static/
RUN mvn -B package -DskipTests
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/ordercraft-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]

