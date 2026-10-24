FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl python3 python3-venv ffmpeg fontconfig \
    fonts-noto-color-emoji libegl1 libgl1 \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir yt-dlp==2026.10.21 \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 container \
    && useradd --uid 10001 --gid container --create-home container
ENV PATH="/opt/yt-dlp/bin:${PATH}" HOME=/home/container
COPY fetch_release.py kita.example.yml /opt/kita/
COPY entrypoint.sh /usr/local/bin/kita-entrypoint
COPY start.sh /usr/local/bin/kita-start
RUN chmod 755 /usr/local/bin/kita-entrypoint /usr/local/bin/kita-start \
    && chmod 644 /opt/kita/fetch_release.py /opt/kita/kita.example.yml
WORKDIR /home/container
USER container
ENTRYPOINT ["/usr/local/bin/kita-entrypoint"]
