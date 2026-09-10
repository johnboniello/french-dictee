/**
 * Dictée FR — family-code sync. One shared word list + one practice-stats blob
 * per "family code". No accounts, no personal data — the code is the only key.
 * Deploy to Cloudflare Workers (free tier) with a KV namespace bound as `LISTS`.
 *
 *   GET  /list/<code>   -> { words:[...], updatedAt:<ms>, replacedAt:<ms> }  (200)
 *                         { }                                                 (404, never synced)
 *   PUT  /list/<code>   body: { words:[...], updatedAt:<ms>, replacedAt:<ms> }
 *
 *   GET  /stats/<code>  -> { stats:{ "<word>": {box,seen,miss,lastMissAt,pinned,text} }, updatedAt } (200)
 *                         { }                                                                          (404)
 *   PUT  /stats/<code>  body: { stats:{...}, updatedAt:<ms> }
 *
 * <code> must match /^[a-z0-9-]{4,40}$/. Lists cap at 500 words; stats cap at
 * 3000 entries — enough for years of weekly lists, small enough that a guessed
 * code can't be used to store junk.
 */

const CODE_RE = /^[a-z0-9-]{4,40}$/;
const MAX_WORDS = 500;
const MAX_WORD_LEN = 40;
const MAX_STATS = 3000;

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

const num = (v, d = 0) => (Number.isFinite(v) ? Math.floor(v) : d);
const clampInt = (v, lo, hi) => Math.min(hi, Math.max(lo, num(v, lo)));

function sanitizeList(payload) {
  if (typeof payload !== "object" || payload === null) return null;
  if (!Array.isArray(payload.words)) return null;
  const seen = new Set();
  const words = [];
  for (const w of payload.words) {
    if (typeof w !== "string") continue;
    const t = w.trim().slice(0, MAX_WORD_LEN);
    if (!t) continue;
    const key = t.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    words.push(t);
    if (words.length >= MAX_WORDS) break;
  }
  return {
    words,
    updatedAt: num(payload.updatedAt, Date.now()),
    replacedAt: num(payload.replacedAt, 0),
  };
}

function sanitizeStats(payload) {
  if (typeof payload !== "object" || payload === null) return null;
  const raw = payload.stats;
  if (typeof raw !== "object" || raw === null) return null;
  const stats = {};
  let n = 0;
  for (const k of Object.keys(raw)) {
    if (n >= MAX_STATS) break;
    if (typeof k !== "string" || k.length > MAX_WORD_LEN) continue;
    const e = raw[k];
    if (typeof e !== "object" || e === null) continue;
    stats[k] = {
      text: typeof e.text === "string" ? e.text.slice(0, MAX_WORD_LEN) : k,
      box: clampInt(e.box, 1, 4),
      seen: clampInt(e.seen, 0, 1e6),
      miss: clampInt(e.miss, 0, 1e6),
      lastMissAt: num(e.lastMissAt, 0),
      pinned: !!e.pinned,
    };
    n++;
  }
  return { stats, updatedAt: num(payload.updatedAt, Date.now()) };
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });

    const url = new URL(request.url);
    const m = url.pathname.match(/^\/(list|stats)\/([^/]+)\/?$/);
    if (!m) return json({ error: "not found" }, 404);

    const kind = m[1];
    const code = decodeURIComponent(m[2]).toLowerCase();
    if (!CODE_RE.test(code)) return json({ error: "bad code" }, 400);

    const key = `${kind}:${code}`;

    if (request.method === "GET") {
      const stored = await env.LISTS.get(key);
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
      const clean = kind === "list" ? sanitizeList(payload) : sanitizeStats(payload);
      if (!clean) return json({ error: `expected a ${kind} payload` }, 400);
      await env.LISTS.put(key, JSON.stringify(clean));
      return json(clean, 200);
    }

    return json({ error: "method not allowed" }, 405);
  },
};
