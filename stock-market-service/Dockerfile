#FROM maven:3.8.3-openjdk-17
#WORKDIR /app
#ADD target/*.jar app.jar
#ENTRYPOINT ["java","-jar","app.jar", "--spring.config.name=application-docker"]

FROM maven:3.9.9-eclipse-temurin-17 AS build
COPY pom.xml /app/
COPY src /app/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -f /app/pom.xml clean package

# Run stage
FROM eclipse-temurin:17-jre-jammy
COPY --from=build /app/target/stock-market-service-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar","/app/app.jar", "--spring.config.name=application-docker"]
