#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$SCRIPT_DIR/results"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"

MODE="${1:-all}"
if [ "$MODE" != "all" ] && [ "$MODE" != "quick" ]; then
  echo "WARN: unknown mode '$MODE'. Starting with mode 'all'"
  MODE="all"
fi

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
  if curl -s --max-time 2 http://localhost:9090/health > /dev/null 2>&1 && \
     curl -s --max-time 2 http://localhost:8080/ready > /dev/null 2>&1; then
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
for i in $(seq 1 20); do
  curl -s --max-time 5 -X POST http://localhost:8080/mock/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"model":"test","messages":[{"role":"user","content":"My name is John Smith, email john@example.com, phone +1-555-123-4567"}]}' > /dev/null 2>&1 || true
done
echo "Warmup complete."
echo ""

echo "[5/7] Running k6 load tests..."
set +e
if [ "$MODE" = "quick" ]; then
  docker compose -f "$COMPOSE_FILE" \
    --profile loadtest run --rm -e QUICK=true k6 run /scripts/loadtest.js
elif [ "$MODE" = "all" ]; then
  docker compose -f "$COMPOSE_FILE" \
    --profile loadtest run --rm k6 run /scripts/loadtest.js
fi
K6_EXIT=$?
set -e
if [ "$K6_EXIT" -ne 0 ]; then
  echo "WARN: k6 exited with code $K6_EXIT (thresholds crossed or error)"
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
