FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app/backend
COPY backend/pom.xml .
COPY backend/src ./src
COPY data/catalog /app/data/catalog
RUN mvn -B package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/lionpathway-api-0.1.0.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
USER 10001
ENTRYPOINT ["java","-jar","app.jar"]
