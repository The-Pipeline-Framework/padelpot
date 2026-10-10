#!/usr/bin/env bash
# Resets synthetic business state and starts fresh native orchestration; leaves UI running for recording.
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
provider="${1:-openrouter}"
case "$provider" in
 openrouter) profile=(); config=pipeline.yaml; : "${OPENROUTER_API_KEY:?Set OPENROUTER_API_KEY; no fallback is enabled}" ;;
 ollama) profile=(-Pollama); config=pipeline.ollama.yaml ;;
 *) echo 'Usage: scripts/reproduce.sh [openrouter|ollama]' >&2; exit 1 ;;
esac
if curl -fsS --max-time 2 http://127.0.0.1:8080/api/view >/dev/null 2>&1; then echo 'Port 8080 already has an application. Stop it before building another demo.' >&2; exit 1; fi
./mvnw -q "${profile[@]}" clean package -DskipTests
python3 scripts/archive-run.py
export PIPELINE_CONFIG="$project_dir/app/src/main/resources/$config"
java -jar app/target/quarkus-app/quarkus-run.jar >output/demo-server.log 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT INT TERM
ready=false
for attempt in {1..120}; do
 if curl -fsS http://127.0.0.1:8080/api/view >/dev/null 2>&1; then ready=true; break; fi
 sleep .5
done
if [[ "$ready" != true ]]; then echo 'Application did not start; see output/demo-server.log.' >&2; exit 1; fi
python3 scripts/e2e.py
echo 'Recording-ready UI: http://127.0.0.1:8080 · Ctrl+C stops the demo.'
wait "$server_pid"
