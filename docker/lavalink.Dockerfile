FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY . .
RUN bash ./gradlew :audio:shadowJar --no-daemon

FROM eclipse-temurin:25-jre-noble
ARG LAVALINK_VERSION=4.2.2
RUN apt-get update && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 kita && useradd --uid 10001 --gid kita --create-home kita
WORKDIR /opt/lavalink
RUN curl -fSL --retry 3 "https://github.com/lavalink-devs/Lavalink/releases/download/${LAVALINK_VERSION}/Lavalink.jar" \
    -o Lavalink.jar && mkdir -p plugins && chown -R kita:kita /opt/lavalink
COPY --from=build --chown=kita:kita /workspace/audio/build/libs/kita-audio.jar ./plugins/
USER kita
ENTRYPOINT ["java", "-jar", "Lavalink.jar"]
