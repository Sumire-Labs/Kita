#!/bin/bash
set -euo pipefail
umask 077
cd /home/container
mkdir -p config data presets
if [[ ! -f config/kita.yml ]]; then
    install -m 600 /opt/kita/kita.example.yml config/kita.yml
fi
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED}"
app_path=$(python3 /opt/kita/fetch_release.py)
exec "$app_path/bin/app" config/kita.yml
