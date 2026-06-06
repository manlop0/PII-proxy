"""Smart mock LLM server for load testing (async).

Receives OpenAI-format chat completion requests, extracts <TYPE_N> tags
from user messages, and generates responses that reference those tags.
Maintains per-session tag history via X-Conversation-Id header.

Endpoints:
  POST /v1/chat/completions — generate tag-aware response
  GET  /health              — readiness check
"""

import asyncio
import json
import random
import re
import sys
import time

from aiohttp import web

TAG_PATTERN = re.compile(r"<[A-Z_]+_\d+>")

RESPONSE_TEMPLATES = [
    "Thank you, {0}. I've noted that.",
    "Got it, {0}. Your {1} is on file.",
    "I see, {0}. Let me record your {1}.",
    "Noted, {0}. Is there anything else?",
    "Perfect, {0}. I have your {1} now.",
    "Understood, {0}. Your {1} has been saved.",
    "Great, {0}. What else can I help with?",
    "I've recorded {0} and {1}. Anything else?",
    "Your {0} and {1} are confirmed.",
    "I see you mentioned {0}. Noted.",
    "Thanks for providing {0}. All set.",
    "I have {0}, {1}, and {2} on record.",
    "Confirmed: {0}, {1}. Moving on.",
    "All details saved: {0}.",
    "Recorded your information: {0} and {1}.",
]

MAX_SESSIONS = 10000
SESSION_TTL = 300

session_tags = {}
session_last_access = {}
request_count = 0


def log(msg):
    print(msg, flush=True, file=sys.stderr)


def cleanup_sessions():
    now = time.time()
    expired = [sid for sid, ts in session_last_access.items() if now - ts > SESSION_TTL]
    for sid in expired:
        session_tags.pop(sid, None)
        session_last_access.pop(sid, None)
    if len(session_tags) > MAX_SESSIONS:
        oldest = sorted(session_last_access, key=session_last_access.get)
        for sid in oldest[: len(session_tags) - MAX_SESSIONS]:
            session_tags.pop(sid, None)
            session_last_access.pop(sid, None)


def extract_tags(messages):
    tags = []
    for msg in messages:
        if msg.get("role") == "user":
            content = msg.get("content", "")
            tags.extend(TAG_PATTERN.findall(content))
    return list(dict.fromkeys(tags))


def generate_response(tags):
    if not tags:
        return "I understand. How can I help you?"

    template = random.choice(RESPONSE_TEMPLATES)
    placeholders = template.count("{")
    fill = tags[: min(placeholders, len(tags))]
    while len(fill) < placeholders:
        fill.append(fill[-1] if fill else "that")

    try:
        return template.format(*fill)
    except (IndexError, KeyError):
        return f"Noted, {tags[0]}. Anything else?"


async def handle_health(request):
    return web.json_response({"status": "UP", "sessions": len(session_tags)})


async def handle_completions(request):
    global request_count

    try:
        raw = await request.read()
        data = json.loads(raw)
    except Exception as e:
        log(f"Parse error: {e}")
        return web.json_response({"error": str(e)}, status=400)

    messages = data.get("messages", [])
    session_id = request.headers.get("X-Conversation-Id", "default")

    request_count += 1
    if request_count % 500 == 0:
        cleanup_sessions()

    if session_id not in session_tags:
        session_tags[session_id] = []

    new_tags = extract_tags(messages)
    for tag in new_tags:
        if tag not in session_tags[session_id]:
            session_tags[session_id].append(tag)

    all_tags = list(session_tags[session_id])
    session_last_access[session_id] = time.time()

    response_content = generate_response(all_tags)

    prompt_tokens = sum(len(m.get("content", "").split()) for m in messages)
    completion_tokens = len(response_content.split())

    response = {
        "id": f"mock-{random.randint(1000, 9999)}",
        "object": "chat.completion",
        "model": "mock-model",
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": response_content},
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
        },
    }

    body = json.dumps(response)
    return web.Response(body=body, content_type="application/json")


async def cleanup_loop():
    while True:
        await asyncio.sleep(60)
        cleanup_sessions()


async def on_startup(app):
    log("Mock server started on port 9090")
    asyncio.create_task(cleanup_loop())


def create_app():
    app = web.Application()
    app.router.add_get("/health", handle_health)
    app.router.add_post("/v1/chat/completions", handle_completions)
    app.on_startup.append(on_startup)
    return app


def main():
    app = create_app()
    web.run_app(app, host="0.0.0.0", port=9090, print=lambda *a: None)


if __name__ == "__main__":
    main()
