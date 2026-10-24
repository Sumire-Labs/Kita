#!/bin/bash
set -euo pipefail
cd /home/container
export HOME=/home/container
exec bash -c "${STARTUP:-/usr/local/bin/kita-start}"
