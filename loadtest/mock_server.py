"""Smart mock LLM server for load testing.

Receives OpenAI-format chat completion requests, extracts <TYPE_N> tags
from user messages, and generates responses that reference those tags.
Maintains per-session tag history via X-Conversation-Id header.

Endpoints:
  POST /v1/chat/completions — generate tag-aware response
  GET  /health              — readiness check
"""

import json
import random
import re
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

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

lock = threading.Lock()
session_tags = {}
session_last_access = {}
request_count = 0


def cleanup_sessions():
    now = time.time()
    with lock:
        expired = [sid for sid, ts in session_last_access.items() if now - ts > SESSION_TTL]
        for sid in expired:
            del session_tags[sid]
            del session_last_access[sid]
        if len(session_tags) > MAX_SESSIONS:
            oldest = sorted(session_last_access, key=session_last_access.get)
            for sid in oldest[:len(session_tags) - MAX_SESSIONS]:
                del session_tags[sid]
                del session_last_access[sid]


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
    fill = tags[:min(placeholders, len(tags))]
    while len(fill) < placeholders:
        fill.append(fill[-1] if fill else "that")

    try:
        return template.format(*fill)
    except (IndexError, KeyError):
        return f"Noted, {tags[0]}. Anything else?"


class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


class MockHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            with lock:
                active = len(session_tags)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "UP", "sessions": active}).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        global request_count

        if self.path != "/v1/chat/completions":
            self.send_response(404)
            self.end_headers()
            return

        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)

        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error": "Invalid JSON"}')
            return

        messages = data.get("messages", [])
        session_id = self.headers.get("X-Conversation-Id", "default")

        with lock:
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

        response = {
            "id": f"mock-{random.randint(1000, 9999)}",
            "object": "chat.completion",
            "model": "mock-model",
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": response_content,
                    },
                    "finish_reason": "stop",
                }
            ],
            "usage": {
                "prompt_tokens": sum(len(m.get("content", "").split()) for m in messages),
                "completion_tokens": len(response_content.split()),
                "total_tokens": 0,
            },
        }
        response["usage"]["total_tokens"] = (
            response["usage"]["prompt_tokens"] + response["usage"]["completion_tokens"]
        )

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(response).encode())

    def log_message(self, format, *args):
        pass


def main():
    server = ThreadingHTTPServer(("0.0.0.0", 9090), MockHandler)
    print("Mock server running on port 9090 (threaded)")
    server.serve_forever()


if __name__ == "__main__":
    main()
