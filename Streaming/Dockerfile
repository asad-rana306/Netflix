# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY src ./src
RUN chmod +x ./mvnw || true
RUN ./mvnw -B -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install FFmpeg for video transcoding
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]