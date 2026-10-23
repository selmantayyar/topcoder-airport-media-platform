# Multi-stage: build the Quarkus fast-jar inside Docker so `docker compose up`
# needs no local Maven/JDK and no pre-build step.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependency layer: cached unless the pom changes.
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN mvn -B -q dependency:go-offline || true
COPY src/ src/
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/quarkus-app/lib/ lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ app/
COPY --from=build /build/target/quarkus-app/quarkus/ quarkus/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
