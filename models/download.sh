#!/bin/bash
set -e

MODEL_NAME="${1:-Babelscape/wikineural-multilingual-ner}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/$(basename "$MODEL_NAME")"

echo "=== PII Proxy Model Downloader ==="
echo "Downloading: $MODEL_NAME"
echo "Output: $OUTPUT_DIR"
echo ""

if ! command -v pip3 &> /dev/null; then
  echo "Error: pip3 not found. Install Python 3 first."
  exit 1
fi

pip3 install --quiet huggingface_hub

echo "Downloading model files..."
huggingface-cli download "$MODEL_NAME" --local-dir "$OUTPUT_DIR"

echo ""
echo "=== Download Complete ==="
echo "Model saved to: $OUTPUT_DIR"
echo ""

CONFIG_JSON="$OUTPUT_DIR/config.json"
if [ -f "$CONFIG_JSON" ]; then
  echo "=== Model Info ==="
  python3 -c "
import json
with open('$CONFIG_JSON') as f:
    cfg = json.load(f)

arch = cfg.get('architectures', ['unknown'])[0]
model_type = cfg.get('model_type', 'unknown')
labels = cfg.get('id2label', {})

print(f'Architecture: {arch} ({model_type})')
print(f'Total labels: {len(labels)}')

# Unique entity types (strip B-/I- prefixes, skip O)
types = set()
for label in labels.values():
    if label != 'O':
        types.add(label.split('-', 1)[-1] if '-' in label else label)

if types:
    print(f'Entity types: {", ".join(sorted(types))}')
"
fi

echo ""
echo "Next steps:"
echo "  1. Convert to ONNX:  python3 $SCRIPT_DIR/convert_to_onnx.py $OUTPUT_DIR"
echo "  2. Update config.yaml: set ml.model_directory and ml.tag_mapping"
echo "  3. Delete original files (optional): keep only model.onnx, tokenizer.json, config.json"
