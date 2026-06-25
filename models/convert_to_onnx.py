"""
Converts a HuggingFace model to ONNX format for use with PII Proxy.

Usage:
    python convert_to_onnx.py <model_dir>
    python convert_to_onnx.py <model_dir> --task token-classification

Requirements:
    pip install optimum[exporters,onnxruntime] transformers

Supported architectures:
    - BERT, RoBERTa, DistilBERT, ALBERT, Electra, DeBERTa (NER, classification)
    - Any model supported by HuggingFace Optimum exporters

NOT supported:
    - GLiNER (custom architecture with multiple models)
    - Models with custom tokenization that cannot be exported

After conversion:
    - Required files: model.onnx, tokenizer.json, config.json
    - Original PyTorch files can be deleted to save space
"""

import sys
import os
import argparse


def main():
    parser = argparse.ArgumentParser(description="Convert HuggingFace model to ONNX format")
    parser.add_argument("model_dir", help="Path to the model directory")
    parser.add_argument("--task", default="token-classification",
                        help="Export task (default: token-classification)")
    parser.add_argument("--output", default=None,
                        help="Output directory (default: same as model_dir)")
    args = parser.parse_args()

    model_dir = args.model_dir
    output_dir = args.output or model_dir

    if not os.path.isdir(model_dir):
        print(f"Error: {model_dir} is not a directory")
        sys.exit(1)

    # Check for required files
    required_files = ["config.json", "tokenizer.json"]
    missing = [f for f in required_files if not os.path.exists(os.path.join(model_dir, f))]
    if missing:
        print(f"Warning: Missing files in {model_dir}: {', '.join(missing)}")
        print("The model may not have a fast tokenizer. Conversion may fail.")

    print(f"Converting {model_dir} to ONNX...")
    print(f"Task: {args.task}")
    print(f"Output: {output_dir}")
    print()

    try:
        from optimum.exporters.onnx import main_export
    except ImportError:
        print("Error: optimum not installed. Run:")
        print("  pip install optimum[exporters,onnxruntime] transformers")
        sys.exit(1)

    try:
        main_export(model_dir, output=output_dir, task=args.task)
    except Exception as e:
        print(f"Error during conversion: {e}")
        print()
        print("Common issues:")
        print("  - Model architecture not supported by Optimum")
        print("  - Missing tokenizer.json (need fast tokenizer)")
        print("  - Custom model structure (e.g., GLiNER)")
        sys.exit(1)

    # Check if ONNX file was created
    onnx_path = os.path.join(output_dir, "model.onnx")
    if os.path.exists(onnx_path):
        size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
        print()
        print(f"=== Conversion Successful ===")
        print(f"ONNX model: {onnx_path} ({size_mb:.1f} MB)")
        print()
        print("Required files for PII Proxy:")
        print(f"  - {onnx_path}")
        print(f"  - {os.path.join(output_dir, 'tokenizer.json')}")
        print(f"  - {os.path.join(output_dir, 'config.json')}")
        print()
        print("Optional: delete original PyTorch files to save space:")
        print(f"  rm {output_dir}/model.safetensors {output_dir}/pytorch_model.bin 2>/dev/null")
    else:
        print("Error: ONNX file was not created")
        sys.exit(1)


if __name__ == "__main__":
    main()
