import { check, sleep } from "k6";
import http from "k6/http";
import { Trend, Counter, Rate } from "k6/metrics";
import { SharedArray } from "k6/data";

const proxyLatency = new Trend("proxy_latency", true);
const baselineLatency = new Trend("baseline_latency", true);
const piiTagsDetected = new Counter("pii_tags_detected");
const errorRate = new Rate("errors");

const PROXY_URL = "http://proxy:8080/mock/v1/chat/completions";
const MOCK_URL = "http://mock:9090/v1/chat/completions";

const QUICK = __ENV.QUICK === "true";

const conversations = new SharedArray("conversations", function () {
  return JSON.parse(open("/scripts/data/conversations.json"));
});

const conversationsLarge = new SharedArray("conversations_large", function () {
  try {
    return JSON.parse(open("/scripts/data/conversations_large.json"));
  } catch {
    return conversations;
  }
});

function sendToProxy(messages, sessionId) {
  const headers = { "Content-Type": "application/json" };
  if (sessionId) {
    headers["X-Conversation-Id"] = sessionId;
  }
  return http.post(
    PROXY_URL,
    JSON.stringify({ model: "gpt-4", messages, stream: false }),
    { headers }
  );
}

function sendToMock(messages) {
  return http.post(
    MOCK_URL,
    JSON.stringify({ model: "gpt-4", messages, stream: false }),
    { headers: { "Content-Type": "application/json" } }
  );
}

function processResponse(res, trend) {
  trend.add(res.timings.duration);
  errorRate.add(res.status !== 200);

  check(res, {
    "status 200": (r) => r.status === 200,
    "has choices": (r) => {
      try {
        return JSON.parse(r.body).choices?.length > 0;
      } catch {
        return false;
      }
    },
  });

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      const content = body.choices?.[0]?.message?.content || "";
      const tags = content.match(/<[A-Z_]+_\d+>/g);
      if (tags) piiTagsDetected.add(tags.length);
      return body.choices?.[0]?.message;
    } catch {
      return null;
    }
  }
  return null;
}

function runConversation(conv, sessionId, trend) {
  const messages = [];

  for (let i = 0; i < conv.turns.length; i++) {
    messages.push(conv.turns[i]);

    const res = sendToProxy(messages, sessionId);
    const assistantMsg = processResponse(res, trend);

    if (assistantMsg) {
      messages.push(assistantMsg);
    }

    sleep(0.1 + Math.random() * 0.4);
  }
}

const quickScenarios = {
  baseline: {
    executor: "constant-vus",
    vus: 10,
    duration: "30s",
    exec: "baseline",
  },
  ephemeral: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "10s", target: 10 },
      { duration: "60s", target: 50 },
      { duration: "10s", target: 0 },
    ],
    exec: "ephemeral",
    startTime: "35s",
  },
};

const fullScenarios = {
  baseline: {
    executor: "constant-vus",
    vus: 10,
    duration: "30s",
    exec: "baseline",
  },
  ephemeral: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "10s", target: 10 },
      { duration: "60s", target: 50 },
      { duration: "10s", target: 0 },
    ],
    exec: "ephemeral",
    startTime: "35s",
  },
  persistent: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "10s", target: 10 },
      { duration: "60s", target: 50 },
      { duration: "10s", target: 0 },
    ],
    exec: "persistent",
    startTime: "120s",
  },
  mixed: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "10s", target: 10 },
      { duration: "60s", target: 50 },
      { duration: "10s", target: 0 },
    ],
    exec: "mixed",
    startTime: "205s",
  },
  spike: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "5s", target: 10 },
      { duration: "5s", target: 200 },
      { duration: "30s", target: 200 },
      { duration: "5s", target: 10 },
      { duration: "5s", target: 0 },
    ],
    exec: "mixed",
    startTime: "290s",
  },
  soak: {
    executor: "constant-vus",
    vus: 50,
    duration: "30m",
    exec: "mixed",
    startTime: "340s",
  },
  large_arrays: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "5s", target: 10 },
      { duration: "50s", target: 30 },
      { duration: "5s", target: 0 },
    ],
    exec: "largeArrays",
    startTime: "2180s",
  },
};

export const options = {
  scenarios: QUICK ? quickScenarios : fullScenarios,
  thresholds: {
    proxy_latency: ["p(95)<2000", "p(99)<5000"],
    errors: ["rate<0.01"],
  },
};

export function baseline() {
  const conv = conversations[__VU % conversations.length];
  const messages = [];

  for (let i = 0; i < Math.min(5, conv.turns.length); i++) {
    messages.push(conv.turns[i]);

    const res = sendToMock(messages);
    baselineLatency.add(res.timings.duration);

    check(res, {
      "baseline status 200": (r) => r.status === 200,
    });

    if (res.status === 200) {
      try {
        const body = JSON.parse(res.body);
        const msg = body.choices?.[0]?.message;
        if (msg) messages.push(msg);
      } catch {}
    }

    sleep(0.1 + Math.random() * 0.4);
  }
}

export function ephemeral() {
  const conv = conversations[__VU % conversations.length];
  runConversation(conv, null, proxyLatency);
}

export function persistent() {
  const conv = conversations[__VU % conversations.length];
  const sessionId = `s-${__VU}-${conv.id}`;
  runConversation(conv, sessionId, proxyLatency);
}

export function mixed() {
  const conv = conversations[__VU % conversations.length];
  const isEphemeral = Math.random() < 0.3;
  const sessionId = isEphemeral ? null : `s-${__VU}-${conv.id}`;
  runConversation(conv, sessionId, proxyLatency);
}

export function largeArrays() {
  const conv = conversationsLarge[__VU % conversationsLarge.length];
  const sessionId = `large-${__VU}-${conv.id}`;
  runConversation(conv, sessionId, proxyLatency);
}
