FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY backend ./backend
ARG SERVICE_MODULE
RUN test -n "$SERVICE_MODULE" && mvn -B -ntp -pl "backend/$SERVICE_MODULE" -am package -DskipTests
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
ARG SERVICE_MODULE
COPY --from=build --chown=app:app /workspace/backend/${SERVICE_MODULE}/target/${SERVICE_MODULE}-1.0.0-SNAPSHOT.jar /app/application.jar
USER app
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:+ExitOnOutOfMemoryError","-jar","/app/application.jar"]
