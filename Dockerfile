FROM openjdk:26-ea-21-jdk-slim
WORKDIR /app
COPY build/libs/ERSPS-0.0.1-SNAPSHOT.jar app.jar
COPY src/main/resources/application.yaml application.yaml
ENTRYPOINT ["java", "-jar", "app.jar"]