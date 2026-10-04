# syntax=docker/dockerfile:1.7
# One Dockerfile for all services: docker build --build-arg MODULE=auth-service .
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY auth-service/pom.xml auth-service/
COPY chat-service/pom.xml chat-service/
COPY gateway/pom.xml gateway/
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ${MODULE} -am dependency:go-offline
COPY auth-service/src auth-service/src
COPY chat-service/src chat-service/src
COPY gateway/src gateway/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ${MODULE} -am package -DskipTests -Djacoco.skip=true \
    && java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-*.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
