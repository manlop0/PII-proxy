"""Parses JVM GC unified log and reports pause statistics.

Usage: python3 analyze_gc.py <path/to/gc.log>

Expected log format (JVM unified logging):
  -Xlog:gc*:file=gc.log:time,uptime,level,tags
"""

import re
import sys
from collections import Counter

GC_LINE = re.compile(
    r"\[(?P<time>[^\]]+)\]\[(?P<uptime>[^\]]+)\]\[(?P<level>[^\]]+)\]\[gc\]"
    r".*GC\(\d+\)\s+Pause\s+(?P<type>\w+).*?"
    r"(?P<before>\d+)([MK])->(?P<after>\d+)([MK])\((?P<total>\d+)([MK])\)\s+"
    r"(?P<duration>[\d.]+)ms"
)


def parse_gc_log(path):
    pauses = []
    with open(path, "r") as f:
        for line in f:
            m = GC_LINE.search(line)
            if m:
                pauses.append({
                    "time": m.group("time"),
                    "uptime": m.group("uptime"),
                    "type": m.group("type"),
                    "before_mb": int(m.group("before")),
                    "after_mb": int(m.group("after")),
                    "total_mb": int(m.group("total")),
                    "duration_ms": float(m.group("duration")),
                })
    return pauses


def analyze(pauses):
    if not pauses:
        print("No GC pauses found.")
        return

    type_counts = Counter(p["type"] for p in pauses)
    durations = sorted(p["duration_ms"] for p in pauses)
    total_time = sum(durations)

    p50_idx = int(len(durations) * 0.5)
    p95_idx = int(len(durations) * 0.95)
    p99_idx = int(len(durations) * 0.99)

    print("=== GC Analysis ===")
    print(f"Total pauses: {len(pauses)}")
    for gc_type, count in type_counts.most_common():
        print(f"  {gc_type}: {count}")
    print(f"Total pause time: {total_time:.1f}ms")
    print(f"Max pause: {durations[-1]:.1f}ms")
    print(f"P50 pause: {durations[p50_idx]:.1f}ms")
    print(f"P95 pause: {durations[p95_idx]:.1f}ms")
    print(f"P99 pause: {durations[p99_idx]:.1f}ms")

    latest = pauses[-1]
    print(f"Final heap: {latest['after_mb']}M / {latest['total_mb']}M")


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <gc.log>")
        sys.exit(1)

    pauses = parse_gc_log(sys.argv[1])
    analyze(pauses)


if __name__ == "__main__":
    main()
