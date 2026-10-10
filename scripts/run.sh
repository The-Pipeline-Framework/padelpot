#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
provider="${1:-openrouter}"
maven_args=(-q)
case "$provider" in
 openrouter)
  if [[ -z "${OPENROUTER_API_KEY:-}" ]]; then
   echo 'Set OPENROUTER_API_KEY before running the default Gemma/OpenRouter demo. No inference fallback is enabled.' >&2
   exit 1
  fi
  export PIPELINE_CONFIG="$project_dir/app/src/main/resources/pipeline.yaml"
  ;;
 ollama) maven_args+=(-Pollama); export PIPELINE_CONFIG="$project_dir/app/src/main/resources/pipeline.ollama.yaml" ;;
 *) echo 'Usage: scripts/run.sh [openrouter|ollama]' >&2; exit 1 ;;
esac
if curl -fsS --max-time 2 http://127.0.0.1:8080/api/view >/dev/null 2>&1; then echo 'Port 8080 already has an application. Stop it before building another demo.' >&2; exit 1; fi
./mvnw "${maven_args[@]}" clean package -DskipTests
exec java -jar app/target/quarkus-app/quarkus-run.jar
