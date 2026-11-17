FROM maven:3.9.10-eclipse-temurin-24 AS build
WORKDIR /build
COPY pom.xml .
COPY Backend/src ./Backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/DataAnaliticsEnterprise-1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
