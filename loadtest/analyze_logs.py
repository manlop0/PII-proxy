"""Parses proxy logs (STDOUT) and reports cache hit rate and latency.

Usage: python3 analyze_logs.py <path/to/proxy.log>

Expected log patterns:
  [DEBUG] Anonymized session=X in 1200µs
  [DEBUG] Anonymized session=X in 1200µs (cache hit)
  [DEBUG] Restored N tags in 'context' for session X in 300µs
  [DEBUG] ML Batch done: size=16, reason=BATCH_FULL, pendingAfterFlush=0
  [INFO] ML Inference: batchSize=16, 400ms (25ms/text)
"""

import re
import sys
from collections import defaultdict

ANON_PATTERN = re.compile(
    r"Anonymized session=(\S+) in (\d+)µs(?: \(cache hit\))?"
)
RESTORE_PATTERN = re.compile(
    r"Restored \d+ tags in '(\S+)' for session (\S+) in (\d+)µs"
)
BATCH_PATTERN = re.compile(
    r"ML Batch done: size=(\d+), reason=(\w+), pendingAfterFlush=(\d+)"
)
INFERENCE_PATTERN = re.compile(
    r"ML Inference: batchSize=(\d+), (\d+)ms \((\d+)ms/text\)"
)


def parse_logs(path):
    anon_hits = 0
    anon_misses = 0
    anon_latencies = []
    restore_latencies = []
    session_anon = defaultdict(list)

    batch_sizes = []
    batch_reasons = defaultdict(int)
    batch_pending = []
    inference_times = []
    inference_per_text = []

    with open(path, "r") as f:
        for line in f:
            m = ANON_PATTERN.search(line)
            if m:
                session = m.group(1)
                latency_us = int(m.group(2))
                is_cache_hit = "cache hit" in line

                anon_latencies.append(latency_us)
                session_anon[session].append(latency_us)

                if is_cache_hit:
                    anon_hits += 1
                else:
                    anon_misses += 1

            m = RESTORE_PATTERN.search(line)
            if m:
                latency_us = int(m.group(3))
                restore_latencies.append(latency_us)

            m = BATCH_PATTERN.search(line)
            if m:
                batch_sizes.append(int(m.group(1)))
                batch_reasons[m.group(2)] += 1
                batch_pending.append(int(m.group(3)))

            m = INFERENCE_PATTERN.search(line)
            if m:
                inference_times.append(int(m.group(2)))
                inference_per_text.append(int(m.group(3)))

    return {
        "anon_hits": anon_hits,
        "anon_misses": anon_misses,
        "anon_latencies": sorted(anon_latencies),
        "restore_latencies": sorted(restore_latencies),
        "session_count": len(session_anon),
        "batch_sizes": sorted(batch_sizes),
        "batch_reasons": dict(batch_reasons),
        "batch_pending": sorted(batch_pending),
        "inference_times": sorted(inference_times),
        "inference_per_text": sorted(inference_per_text),
    }


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0
    idx = int(len(sorted_vals) * p / 100)
    idx = min(idx, len(sorted_vals) - 1)
    return sorted_vals[idx]


def analyze(stats):
    total = stats["anon_hits"] + stats["anon_misses"]
    lat = stats["anon_latencies"]
    rest = stats["restore_latencies"]

    print("=== Cache Hit Analysis ===")
    if total > 0:
        hit_rate = stats["anon_hits"] / total * 100
        print(f"Total anonymizations: {total}")
        print(f"Cache hits: {stats['anon_hits']} ({hit_rate:.1f}%)")
        print(f"Cache misses: {stats['anon_misses']}")
    else:
        print("No anonymization events found.")

    print(f"Unique sessions: {stats['session_count']}")

    print()
    print("=== Anonymization Latency ===")
    if lat:
        print(f"Count: {len(lat)}")
        print(f"P50: {percentile(lat, 50)}µs ({percentile(lat, 50)/1000:.1f}ms)")
        print(f"P95: {percentile(lat, 95)}µs ({percentile(lat, 95)/1000:.1f}ms)")
        print(f"P99: {percentile(lat, 99)}µs ({percentile(lat, 99)/1000:.1f}ms)")
        print(f"Max: {lat[-1]}µs ({lat[-1]/1000:.1f}ms)")
    else:
        print("No anonymization latency data found.")

    print()
    print("=== Restore Latency ===")
    if rest:
        print(f"Count: {len(rest)}")
        print(f"P50: {percentile(rest, 50)}µs ({percentile(rest, 50)/1000:.1f}ms)")
        print(f"P95: {percentile(rest, 95)}µs ({percentile(rest, 95)/1000:.1f}ms)")
        print(f"P99: {percentile(rest, 99)}µs ({percentile(rest, 99)/1000:.1f}ms)")
        print(f"Max: {rest[-1]}µs ({rest[-1]/1000:.1f}ms)")
    else:
        print("No restore latency data found.")

    bs = stats["batch_sizes"]
    bp = stats["batch_pending"]
    br = stats["batch_reasons"]
    it = stats["inference_times"]
    ipt = stats["inference_per_text"]

    print()
    print("=== ML Batching ===")
    if bs:
        print(f"Total batches: {len(bs)}")
        print(f"Batch size P50: {percentile(bs, 50)}, P95: {percentile(bs, 95)}, Max: {bs[-1]}")
        print(f"Flush reasons: {br}")
        print(f"Pending after flush P50: {percentile(bp, 50)}, P95: {percentile(bp, 95)}, Max: {bp[-1]}")
    else:
        print("No batch metrics found.")

    print()
    print("=== ML Inference ===")
    if it:
        print(f"Total inferences: {len(it)}")
        print(f"Inference time P50: {percentile(it, 50)}ms, P95: {percentile(it, 95)}ms, Max: {it[-1]}ms")
        print(f"Per-text time P50: {percentile(ipt, 50)}ms, P95: {percentile(ipt, 95)}ms, Max: {ipt[-1]}ms")
    else:
        print("No inference metrics found.")


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <proxy.log>")
        sys.exit(1)

    stats = parse_logs(sys.argv[1])
    analyze(stats)


if __name__ == "__main__":
    main()
