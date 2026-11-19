# Single-service image: Expo web build served by the Spring Boot API on one origin.
# Development/test deployment only - authentication is not production-ready.

FROM node:22-bookworm-slim AS web
WORKDIR /web
COPY apps/mobile/package.json apps/mobile/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY apps/mobile/ ./
# '/' = call the API on the same origin that serves the page.
ENV EXPO_PUBLIC_API_URL=/
RUN npx expo export --platform web --output-dir dist

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY services/api/pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline
COPY services/api/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -ntp -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=api /api/target/electroride-api-*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
