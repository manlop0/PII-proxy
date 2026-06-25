"""
Prints model architecture info from a HuggingFace model's config.json.

Usage:
    python3 model_info.py <path-to-config.json>
"""

import json
import sys


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 model_info.py <path-to-config.json>")
        sys.exit(1)

    config_path = sys.argv[1]
    with open(config_path) as f:
        cfg = json.load(f)

    arch = cfg.get('architectures', ['unknown'])[0]
    model_type = cfg.get('model_type', 'unknown')
    labels = cfg.get('id2label', {})

    print(f'Architecture: {arch} ({model_type})')
    print(f'Total labels: {len(labels)}')

    types = set()
    for label in labels.values():
        if label != 'O':
            types.add(label.split('-', 1)[-1] if '-' in label else label)

    if types:
        print(f'Entity types: {", ".join(sorted(types))}')


if __name__ == "__main__":
    main()