#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$SCRIPT_DIR/results"

echo "=== PII Proxy Load Testing ==="
echo ""

mkdir -p "$RESULTS_DIR"

echo "[1/7] Generating conversations..."
python3 "$SCRIPT_DIR/conversations.py"
echo ""

echo "[2/7] Building Docker images..."
docker compose -f "$PROJECT_DIR/docker-compose.yml" build proxy mock
echo ""

echo "[3/7] Starting mock and proxy..."
docker compose -f "$PROJECT_DIR/docker-compose.yml" up -d mock proxy
echo ""

echo "[4/7] Waiting for services..."
for i in $(seq 1 30); do
  if curl -s http://localhost:9090/health > /dev/null 2>&1 && \
     curl -s http://localhost:8080/ready > /dev/null 2>&1; then
    echo "Services ready!"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: Services not ready after 30s"
    docker compose -f "$PROJECT_DIR/docker-compose.yml" logs
    docker compose -f "$PROJECT_DIR/docker-compose.yml" down
    exit 1
  fi
  sleep 1
done
echo ""

echo "[5/7] Running k6 load tests..."
docker compose -f "$PROJECT_DIR/docker-compose.yml" \
  --profile loadtest run --rm k6 run /scripts/loadtest.js
echo ""

echo "[6/7] Collecting results..."
docker compose -f "$PROJECT_DIR/docker-compose.yml" cp proxy:/app/gc.log "$RESULTS_DIR/gc.log" 2>/dev/null || true
docker compose -f "$PROJECT_DIR/docker-compose.yml" logs proxy > "$RESULTS_DIR/proxy.log" 2>&1
echo "Results saved to $RESULTS_DIR/"
echo ""

echo "[7/7] Analyzing results..."
echo "---"
python3 "$SCRIPT_DIR/analyze_gc.py" "$RESULTS_DIR/gc.log"
echo ""
echo "---"
python3 "$SCRIPT_DIR/analyze_logs.py" "$RESULTS_DIR/proxy.log"
echo ""

echo "=== Cleanup ==="
docker compose -f "$PROJECT_DIR/docker-compose.yml" down
echo "Done!"
