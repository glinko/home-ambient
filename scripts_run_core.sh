#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# Infra
cd infra/docker
docker compose up -d postgres minio
cd "$ROOT"

# API
cd services/ingest-api
if [ ! -d node_modules ]; then npm install; fi
export DATABASE_URL="postgres://postgres:postgres@127.0.0.1:5432/homeambient"
export PORT=8070
nohup node src/server.js > /tmp/home-ambient-ingest.log 2>&1 &
echo $! > /tmp/home-ambient-ingest.pid

HOST_IP=$(hostname -I | awk {print })
echo "Core started. Ingest: http://${HOST_IP}:8070/health"
