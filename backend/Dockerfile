FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Separate layer so dependencies are cached until pom.xml changes
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system app
COPY --from=build /app/target/backend-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
