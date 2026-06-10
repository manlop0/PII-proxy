#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$SCRIPT_DIR/results"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"

MODE="${1:-all}"

echo "=== PII Proxy Load Testing (mode: $MODE) ==="
echo ""

mkdir -p "$RESULTS_DIR"

echo "[1/7] Generating conversations..."
python3 "$SCRIPT_DIR/conversations.py" --turns 15 --count 200
if [ "$MODE" = "all" ] || [ "$MODE" = "large_arrays" ]; then
  python3 "$SCRIPT_DIR/conversations.py" --turns 30 --count 50 \
    --output "$SCRIPT_DIR/scripts/data/conversations_large.json"
fi
echo ""

echo "[2/7] Building Docker images..."
docker compose -f "$COMPOSE_FILE" build proxy mock
echo ""

echo "[3/7] Starting mock and proxy..."
docker compose -f "$COMPOSE_FILE" up -d mock proxy
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
    docker compose -f "$COMPOSE_FILE" logs
    docker compose -f "$COMPOSE_FILE" down
    exit 1
  fi
  sleep 1
done
echo ""

echo ""
echo "[4.5/7] Warming up ML model..."
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/mock/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"model":"test","messages":[{"role":"user","content":"warmup request"}]}' > /dev/null 2>&1 || true
done
echo "Warmup complete."
echo ""

echo "[5/7] Running k6 load tests..."
if [ "$MODE" = "quick" ]; then
  docker compose -f "$COMPOSE_FILE" \
    --profile loadtest run --rm -e QUICK=true k6 run /scripts/loadtest.js
elif [ "$MODE" = "all" ]; then
  docker compose -f "$COMPOSE_FILE" \
    --profile loadtest run --rm k6 run /scripts/loadtest.js
else
  docker compose -f "$COMPOSE_FILE" \
    --profile loadtest run --rm k6 run /scripts/loadtest.js --scenario "$MODE"
fi
echo ""

echo "[6/7] Collecting results..."
docker compose -f "$COMPOSE_FILE" cp proxy:/app/gc.log "$RESULTS_DIR/gc.log" 2>/dev/null || true
docker compose -f "$COMPOSE_FILE" logs proxy > "$RESULTS_DIR/proxy.log" 2>&1
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
docker compose -f "$COMPOSE_FILE" down
echo "Done!"
