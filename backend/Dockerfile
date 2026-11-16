# Store MIS backend: build with Maven, run on a slim Java 21 runtime as a non-root user.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests && cp target/storemis.jar /storemis.jar

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Kabul \
    SPRING_PROFILES_ACTIVE=prod \
    STOREMIS_FILES_DIR=/data/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
RUN groupadd --system storemis && useradd --system --gid storemis --home /app storemis \
    && mkdir -p /app /data/files && chown -R storemis:storemis /app /data
WORKDIR /app
COPY --from=build /storemis.jar /app/storemis.jar
USER storemis
EXPOSE 8080
# Plain bash TCP check, so the image needs no curl.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/storemis.jar"]
