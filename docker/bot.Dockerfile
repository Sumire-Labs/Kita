FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY . .
RUN bash ./gradlew :app:installDist --no-daemon

FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl python3 python3-venv ffmpeg \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir yt-dlp==2026.8.19 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 kita && useradd --uid 10001 --gid kita --create-home kita
ENV PATH="/opt/yt-dlp/bin:${PATH}"
WORKDIR /opt/kita
COPY --from=build --chown=kita:kita /workspace/app/build/install/app ./app
RUN mkdir -p config data presets && chown -R kita:kita /opt/kita
USER kita
ENTRYPOINT ["/opt/kita/app/bin/app"]
