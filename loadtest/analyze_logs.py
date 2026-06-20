"""Parses proxy logs (STDOUT) and reports cache hit rate and latency.

Usage: python3 analyze_logs.py <path/to/proxy.log>

Expected log patterns:
  [DEBUG] Pipeline: total=123ms, cache=1ms, regex=5ms, subst=15ms
  [DEBUG] Pipeline: total=123ms, cache=1ms, regex=0, ml=0, subst=0 (hit)
  [DEBUG] Restored N tags in 'context' for session X in 300µs
  [DEBUG] ML Batch done: size=16, reason=BATCH_FULL, pendingAfterFlush=0
  [INFO] ML Inference: batchSize=16, 400ms (25ms/text)
"""

import re
import sys
from collections import defaultdict

PIPELINE_PATTERN = re.compile(
    r"Pipeline: total=(\d+)ms, cache=(\d+)ms, regex=(\d+)ms(?:, ml=(\d+)ms)?(?:, subst=(\d+)ms)?"
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
    pipeline_cache = []
    pipeline_regex = []
    pipeline_subst = []
    pipeline_total = []
    pipeline_hits = 0
    restore_latencies = []

    batch_sizes = []
    batch_reasons = defaultdict(int)
    batch_pending = []
    inference_times = []
    inference_per_text = []

    with open(path, "r") as f:
        for line in f:
            m = PIPELINE_PATTERN.search(line)
            if m:
                total_ms = int(m.group(1))
                cache_ms = int(m.group(2))
                regex_ms = int(m.group(3))
                is_hit = "(hit)" in line

                pipeline_total.append(total_ms)
                pipeline_cache.append(cache_ms)
                pipeline_regex.append(regex_ms)

                if is_hit:
                    pipeline_hits += 1
                else:
                    # subst is present for non-hit lines
                    if m.group(5):
                        pipeline_subst.append(int(m.group(5)))

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
        "pipeline_total": sorted(pipeline_total),
        "pipeline_cache": sorted(pipeline_cache),
        "pipeline_regex": sorted(pipeline_regex),
        "pipeline_subst": sorted(pipeline_subst),
        "pipeline_hits": pipeline_hits,
        "pipeline_misses": len(pipeline_total) - pipeline_hits,
        "restore_latencies": sorted(restore_latencies),
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
    pt = stats["pipeline_total"]
    pc = stats["pipeline_cache"]
    pr = stats["pipeline_regex"]
    ps = stats["pipeline_subst"]
    ph = stats["pipeline_hits"]
    pm = stats["pipeline_misses"]
    rest = stats["restore_latencies"]

    print("=== Cache Hit Analysis ===")
    total = ph + pm
    if total > 0:
        hit_rate = ph / total * 100
        print(f"Total anonymizations: {total}")
        print(f"Cache hits: {ph} ({hit_rate:.1f}%)")
        print(f"Cache misses: {pm}")
    else:
        print("No anonymization events found.")

    print()
    print("=== Pipeline Breakdown ===")
    if pt:
        print(f"Total anonymizations: {len(pt)}")
        print()
        print("Component timing (avg):")
        avg_total = sum(pt) / len(pt)
        avg_cache = sum(pc) / len(pc)
        avg_regex = sum(pr) / len(pr)
        avg_subst = sum(ps) / len(ps) if ps else 0
        # ML time = total - cache - regex - subst (approximate)
        avg_ml = avg_total - avg_cache - avg_regex - avg_subst
        if avg_ml < 0:
            avg_ml = 0

        print(f"  Cache check:  {avg_cache:.1f}ms ({avg_cache/avg_total*100:.1f}%)")
        print(f"  Regex filters: {avg_regex:.1f}ms ({avg_regex/avg_total*100:.1f}%)")
        print(f"  ML inference: {avg_ml:.1f}ms ({avg_ml/avg_total*100:.1f}%)")
        print(f"  Substitution: {avg_subst:.1f}ms ({avg_subst/avg_total*100:.1f}%)")
        print(f"  Total:        {avg_total:.1f}ms")
    else:
        print("No pipeline metrics found.")

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
