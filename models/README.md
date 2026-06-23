# Models

PII Proxy uses ONNX Runtime for ML-based NER (Named Entity Recognition). This directory contains the model files.

## Quick Start

```bash
# Download a model from HuggingFace
./download.sh Babelscape/wikineural-multilingual-ner

# Convert to ONNX format
python3 convert_to_onnx.py Babelscape/wikineural-multilingual-ner

# Update config.yaml
# Set ml.model_directory to the model path
```

## Supported Model Types

| Architecture | Example Models | Status |
|---|---|---|
| BERT-based NER | Babelscape/wikineural-multilingual-ner, Davlan/bert-base-multilingual-cased-ner-hrl | ✅ |
| RoBERTa NER | Jean-Baptiste/camembert-ner | ✅ |
| DistilBERT NER | distilbert-base-cased-finetuned-conll03-english | ✅ |
| GLiNER | urchade/gliner_large-v2.1 | ❌ Custom architecture |
| Multi-file models | Various | ❌ Not supported |

## Requirements

- Model must have `tokenizer.json` (fast tokenizer)
- Model must be exportable by HuggingFace Optimum
- Standard BERT/RoBERTa/DistilBERT architectures work best

## Checking Model Compatibility

1. **Has fast tokenizer?**
   ```bash
   ls <model_dir>/tokenizer.json
   ```
   If missing, the model uses slow tokenizers and may not work.

2. **Standard architecture?**
   Check `config.json` for `"model_type"`:
   - `bert`, `roberta`, `distilbert`, `albert`, `electra`, `deberta` — ✅ supported
   - `gliner`, custom types — ❌ not supported

3. **Test export:**
   ```bash
   pip install optimum[exporters]
   optimum-cli export onnx --help  # check supported tasks
   ```

## Files After Conversion

After running `convert_to_onnx.py`, you need:

| File | Purpose |
|---|---|
| `model.onnx` | ONNX model (quantized) |
| `tokenizer.json` | Fast tokenizer |
| `config.json` | Model config (labels, architecture) |

Optional files that can be deleted:
- `model.safetensors`, `pytorch_model.bin` — PyTorch weights
- `vocab.txt` — vocabulary (included in tokenizer.json)
- `special_tokens_map.json`, `tokenizer_config.json` — tokenizer config

## Configuration

Update `config.yaml` in the project root:

```yaml
ml:
  model_directory: "./models/Babelscape"  # path to model directory
  output_adapter: "BIO"                    # BIO for BERT-based, SIMPLE for flat tags
  tag_mapping:
    LOC: "LOCATION"
    PER: "PERSON"
    ORG: "ORGANIZATION"
```

## Custom Models

To use a custom model:

1. Ensure it's a standard HuggingFace transformers model
2. Export to ONNX using `convert_to_onnx.py`
3. Update `config.yaml` with the correct `output_adapter`:
   - `BIO` — for models using BIO/BIOES tagging (most NER models)
   - `SIMPLE` — for models using flat tags without B-/I- prefixes
   - FQCN (e.g. `com.example.MyAdapter`) — for custom labeling schemes
4. Configure `tag_mapping` to rename model tags. Two purposes:
   - **Readable names** — `PER` → `PERSON`, `LOC` → `LOCATION`
   - **LLM context** — descriptive tags help LLM understand entity types better

## Troubleshooting

**"Model directory not found"**
- Check `ml.model_directory` in config.yaml
- Use absolute paths or paths relative to the working directory

**"Missing required ML file: *.onnx"**
- Run `convert_to_onnx.py` to create the ONNX file

**"ML Inference batch failed"**
- Check model compatibility (must support token-classification)
- Ensure tokenizer.json exists

**Slow inference**
- Reduce `ml.batch_size` for lower latency
- Increase `ml.intra_op_threads` for faster per-batch inference (uses more CPU)
