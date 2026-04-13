FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY stacksage-common/pom.xml stacksage-common/
COPY stacksage-server/pom.xml stacksage-server/
COPY stacksage-cli/pom.xml stacksage-cli/

COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl stacksage-common,stacksage-server -B

COPY stacksage-common stacksage-common
COPY stacksage-server stacksage-server
RUN ./mvnw package -pl stacksage-common,stacksage-server -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S stacksage && adduser -S stacksage -G stacksage
RUN mkdir -p /app/uploads && chown stacksage:stacksage /app/uploads

COPY --from=build /workspace/stacksage-server/target/*.jar app.jar

EXPOSE 8080
USER stacksage

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
