FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -DskipTests package && mv target/*.jar target/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/app.jar app.jar

EXPOSE 8081
ENV SERVER_PORT=8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

