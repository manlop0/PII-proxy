# Load Testing for PII Proxy

## Quick Start

```bash
# Run quick scenarios (default: baseline + ephemeral)
./loadtest/run.sh

# Run all scenarios
./loadtest/run.sh all
```

This will:
1. Generate test conversations from `data/`
2. Build Docker images
3. Start mock server and proxy
4. Warm up ML model (5 requests with PII)
5. Run k6 scenarios
6. Collect and analyze results
7. Clean up containers

## Architecture

```
k6 (Docker)  →  proxy (:8080)  →  mock (:9090)
   │                  │                │
   │            anonymize PII     smart tag
   │            + restore PII     responses
   │                  │                
   └── metrics ───────┘                
   └── gc.log                          
```

## Scenarios

### Quick mode (default)
| Scenario | VUs | Duration | Description |
|---|---|---|---|
| baseline | 10 constant | 30s | Direct to mock (no proxy overhead) |
| ephemeral | 10→50→0 | 80s | No X-Conversation-Id header |

### All mode
| Scenario | VUs | Duration | Description |
|---|---|---|---|
| baseline | 10 constant | 30s | Direct to mock (no proxy overhead) |
| ephemeral | 10→50→0 | 80s | No X-Conversation-Id header |
| persistent | 10→50→0 | 80s | With X-Conversation-Id |
| mixed | 10→50→0 | 80s | 70% persistent + 30% ephemeral |
| spike | 10→200→10 | 50s | Sudden burst (ML queue stress) |
| soak | 50 constant | 10m | Memory leak detection |
| large_arrays | 10→30→0 | 60s | 30-turn conversations |
| cache_test | 5 shared | 200 iter | Incremental conversation with cache |

## Metrics

### k6 metrics
- `proxy_latency` — p50/p95/p99 of proxy response time
- `baseline_latency` — reference latency (direct to mock)
- `pii_tags_detected` — number of tags in responses
- `error_rate` — 5xx error rate

### Proxy logs (analyze_logs.py)
- Cache hit rate
- Anonymization latency (p50/p95/p99)
- Restore latency (p50/p95/p99)
- ML Batching: batch size, flush reasons, pending after flush
- ML Inference: inference time, per-text time
- Pipeline breakdown: cache, regex, substitution components

### GC logs (analyze_gc.py)
- GC pause count (young/mixed/full)
- Max/p99 pause duration
- Heap usage

## Files

```
loadtest/
├── data/
│   ├── entities.json       # PII entity pools
│   └── templates.json      # Message templates
├── mock_server.py          # Smart mock (tag-aware responses)
├── conversations.py        # Conversation generator
├── scripts/
│   ├── loadtest.js         # k6 scenarios
│   └── data/
│       └── conversations.json  # Generated (gitignored)
├── config.loadtest.yaml    # Proxy config for load testing
├── analyze_gc.py           # GC log parser
├── analyze_logs.py         # Proxy log parser
├── run.sh                  # Orchestrator
├── docker-compose.yml      # Load testing services
├── results/                # Test results (gitignored)
├── Dockerfile              # Python mock image
└── README.md               # This file
```

## Manual Run

```bash
# Generate conversations
python3 loadtest/conversations.py

# Start services (from loadtest/ directory)
docker compose up -d mock proxy

# Wait for services
curl --max-time 2 http://localhost:8080/ready

# Warm up ML model
for i in $(seq 1 5); do
  curl -s --max-time 5 -X POST http://localhost:8080/mock/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"model":"test","messages":[{"role":"user","content":"My name is John Smith, email john@example.com"}]}'
done

# Run specific scenario
docker compose --profile loadtest run --rm k6 run /scripts/loadtest.js \
  --scenario persistent

# Collect results
docker compose cp proxy:/app/gc.log ./results/
docker compose logs proxy > ./results/proxy.log

# Analyze
python3 analyze_gc.py ./results/gc.log
python3 analyze_logs.py ./results/proxy.log

# Cleanup
docker compose down
```
