/**
 * Dictée FR — family-code word-list sync.
 *
 * A tiny key/value API: one shared word list per "family code". No accounts,
 * no personal data — the code itself is the only key. Deploy to Cloudflare
 * Workers (free tier) with a KV namespace bound as `LISTS`.
 *
 *   GET  /list/<code>              -> { "words": [...], "updatedAt": <ms> }   (200)
 *                                    { }                                       (404, never synced)
 *   PUT  /list/<code>  body: { "words": [...], "updatedAt": <ms> }
 *                                 -> the stored object                        (200)
 *
 * <code> must match /^[a-z0-9-]{4,40}$/. Lists are capped at 500 words of
 * 40 chars each so a guessed code can't be used to store junk.
 */

const CODE_RE = /^[a-z0-9-]{4,40}$/;
const MAX_WORDS = 500;
const MAX_WORD_LEN = 40;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Access-Control-Max-Age": "86400",
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

function sanitize(payload) {
  if (typeof payload !== "object" || payload === null) return null;
  const rawWords = Array.isArray(payload.words) ? payload.words : null;
  if (!rawWords) return null;
  const seen = new Set();
  const words = [];
  for (const w of rawWords) {
    if (typeof w !== "string") continue;
    const t = w.trim().slice(0, MAX_WORD_LEN);
    if (!t) continue;
    const key = t.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    words.push(t);
    if (words.length >= MAX_WORDS) break;
  }
  const updatedAt = Number.isFinite(payload.updatedAt) ? Math.floor(payload.updatedAt) : Date.now();
  return { words, updatedAt };
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });

    const url = new URL(request.url);
    const m = url.pathname.match(/^\/list\/([^/]+)\/?$/);
    if (!m) return json({ error: "not found" }, 404);

    const code = decodeURIComponent(m[1]).toLowerCase();
    if (!CODE_RE.test(code)) return json({ error: "bad code" }, 400);

    if (request.method === "GET") {
      const stored = await env.LISTS.get(`list:${code}`);
      if (!stored) return json({}, 404);
      return new Response(stored, {
        status: 200,
        headers: { "Content-Type": "application/json", ...CORS },
      });
    }

    if (request.method === "PUT") {
      let payload;
      try {
        payload = await request.json();
      } catch {
        return json({ error: "bad json" }, 400);
      }
      const clean = sanitize(payload);
      if (!clean) return json({ error: "expected { words: [...] }" }, 400);
      await env.LISTS.put(`list:${code}`, JSON.stringify(clean));
      return json(clean, 200);
    }

    return json({ error: "method not allowed" }, 405);
  },
};
