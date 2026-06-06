"""Parses proxy logs (STDOUT) and reports cache hit rate and latency.

Usage: python3 analyze_logs.py <path/to/proxy.log>

Expected log patterns:
  [DEBUG] Anonymized session=X in 1200µs
  [DEBUG] Anonymized session=X in 1200µs (cache hit)
  [DEBUG] Restored N tags in 'context' for session X in 300µs
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


def parse_logs(path):
    anon_hits = 0
    anon_misses = 0
    anon_latencies = []
    restore_latencies = []
    session_anon = defaultdict(list)

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

    return {
        "anon_hits": anon_hits,
        "anon_misses": anon_misses,
        "anon_latencies": sorted(anon_latencies),
        "restore_latencies": sorted(restore_latencies),
        "session_count": len(session_anon),
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


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <proxy.log>")
        sys.exit(1)

    stats = parse_logs(sys.argv[1])
    analyze(stats)


if __name__ == "__main__":
    main()
