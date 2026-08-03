# Multi-stage Dockerfile: build with Maven, run with a lightweight JRE

# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# copy maven wrapper and settings (if present) + pom
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
# copy sources
COPY src ./src
# run the build (use the wrapper if present)
RUN chmod +x ./mvnw || true
RUN ./mvnw -B -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
# copy the built jar (artifact name produced by build)
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]

