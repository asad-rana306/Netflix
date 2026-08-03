FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser:appuser

# Copies the JAR compiled directly on your Mac
COPY target/*.jar app.jar

EXPOSE 8082

ENV JAVA_OPTS="-Xms1g -Xmx3g -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]