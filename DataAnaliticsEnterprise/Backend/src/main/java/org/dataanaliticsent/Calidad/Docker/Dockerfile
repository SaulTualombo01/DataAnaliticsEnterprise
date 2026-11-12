FROM node:24-bookworm-slim AS frontend
WORKDIR /build/Frontend
COPY Frontend/package.json Frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-fund
COPY Frontend/ ./
RUN npm run build

FROM maven:3.9.15-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
COPY Backend/ Backend/
COPY Docs/Arquitectura/ Docs/Arquitectura/
COPY --from=frontend /build/target/frontend/ target/frontend/
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install --only-upgrade --yes apt libapt-pkg6.0 && rm -rf /var/lib/apt/lists/*
RUN groupadd --system m2 && useradd --system --gid m2 --home /app m2
WORKDIR /app
RUN mkdir -p /app/data/csv-inputs && chown -R m2:m2 /app/data
COPY --from=backend --chown=m2:m2 /build/target/DataAnaliticsEnterprise-1.0-SNAPSHOT.jar app.jar
COPY --from=backend --chown=m2:m2 /build/target/classes/org/dataanaliticsent/Calidad/Docker/ /app/health/org/dataanaliticsent/Calidad/Docker/
USER m2
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
