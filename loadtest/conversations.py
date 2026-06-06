"""Generates multi-turn conversations from entities.json and templates.json.

Reads PII entity pools and message templates, produces N conversations
with M user turns each. Randomized template selection ensures unique text
while keeping entities consistent (same PII values -> same proxy tags).

Usage:
  python3 conversations.py
  python3 conversations.py --turns 30 --count 50 --output scripts/data/conversations_large.json
"""

import argparse
import json
import os
import random

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
DEFAULT_OUTPUT = os.path.join(SCRIPT_DIR, "scripts", "data", "conversations.json")

PII_TURNS_RATIO = 0.6

ENTITY_TO_TEMPLATE = {
    "PERSON": "intro",
    "LOCATION": "location",
    "PHONE": "phone",
    "ORG": "org",
    "EMAIL": "email",
    "CREDIT_CARD": "card",
    "IP_ADDRESS": "ip",
}


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def pick_entities(entities_pool, rng):
    types = list(entities_pool.keys())
    num_types = rng.randint(3, min(7, len(types)))
    selected_types = rng.sample(types, num_types)
    return {t: rng.choice(entities_pool[t]) for t in selected_types}


def generate_turns(templates, entities, rng, num_turns):
    user_templates = templates["user"]
    free_templates = user_templates["free"]

    pii_template_keys = [ENTITY_TO_TEMPLATE[t] for t in entities if t in ENTITY_TO_TEMPLATE]
    num_pii = max(1, int(num_turns * PII_TURNS_RATIO))
    num_pii = min(num_pii, num_turns)
    pii_keys_used = rng.choices(pii_template_keys, k=num_pii)

    pii_turns = []
    for tpl_key in pii_keys_used:
        template = rng.choice(user_templates[tpl_key])
        content = template
        for etype, value in entities.items():
            placeholder = "{" + etype + "}"
            content = content.replace(placeholder, value)
        pii_turns.append(content)

    num_free = num_turns - num_pii
    free_turns = [rng.choice(free_templates) for _ in range(num_free)]

    turns = []
    pii_idx = 0
    free_idx = 0
    for i in range(num_turns):
        if i == 0:
            turns.append(pii_turns[pii_idx])
            pii_idx += 1
        elif pii_idx < len(pii_turns) and (free_idx >= len(free_turns) or rng.random() < 0.55):
            turns.append(pii_turns[pii_idx])
            pii_idx += 1
        else:
            turns.append(free_turns[free_idx])
            free_idx += 1

    return [{"role": "user", "content": t} for t in turns]


def main():
    parser = argparse.ArgumentParser(description="Generate test conversations")
    parser.add_argument("--turns", type=int, default=15, help="Number of turns per conversation")
    parser.add_argument("--count", type=int, default=200, help="Number of conversations")
    parser.add_argument("--output", type=str, default=DEFAULT_OUTPUT, help="Output file path")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    args = parser.parse_args()

    entities_pool = load_json(os.path.join(DATA_DIR, "entities.json"))
    templates = load_json(os.path.join(DATA_DIR, "templates.json"))

    rng = random.Random(args.seed)

    conversations = []
    for i in range(args.count):
        entities = pick_entities(entities_pool, rng)
        turns = generate_turns(templates, entities, rng, args.turns)

        conversations.append({
            "id": f"conv-{i:04d}",
            "entities": entities,
            "turns": turns,
        })

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(conversations, f, indent=2, ensure_ascii=False)

    total_turns = sum(len(c["turns"]) for c in conversations)
    print(f"Generated {len(conversations)} conversations, {total_turns} total turns")
    print(f"Turns per conversation: {args.turns}")
    print(f"Output: {args.output}")


if __name__ == "__main__":
    main()
