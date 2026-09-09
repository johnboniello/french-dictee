# Dictée FR — family-code sync API

A ~90-line Cloudflare Worker that stores **one shared word list per "family code"**.
No accounts, no login, no personal data: the code *is* the key. Anyone who knows a
code can read and overwrite that one list, so use the long random code the app
generates for you.

## What it stores

`KV` key `list:<code>` → `{"words": ["école", "jeudi", ...], "updatedAt": 1725900000000}`

- `<code>` must match `^[a-z0-9-]{4,40}$`
- lists are capped at 500 words × 40 chars

## Deploy (no Node needed — dashboard route)

1. Sign in at <https://dash.cloudflare.com> → **Workers & Pages** → **Create** → **Create Worker**.
2. Name it `dictee-sync`, **Deploy**, then **Edit code**.
3. Paste the entire contents of [`worker.js`](worker.js), **Save and deploy**.
4. **Storage & Databases → KV → Create namespace**, name it `LISTS`.
5. Back in the Worker → **Settings → Bindings → Add → KV namespace**:
   variable name `LISTS`, namespace `LISTS`. **Deploy**.
6. Your endpoint is `https://dictee-sync.<your-subdomain>.workers.dev`.

## Deploy (with Node / Wrangler)

```bash
npx wrangler kv namespace create LISTS      # copy the printed id
# paste it into wrangler.toml -> kv_namespaces.id
npx wrangler deploy
```

## Point the apps at it

- **Android**: set `SYNC_BASE_URL` in
  `app/src/main/java/com/johnb/frenchspelling/Sync.kt` to the endpoint above
  (no trailing slash), then rebuild.
- **PWA**: set `SYNC_BASE_URL` near the top of
  `johnboniello.github.io/src/dictee/app.js`, bump the service-worker cache
  version, and push.

## Quick test

```bash
BASE=https://dictee-sync.<your-subdomain>.workers.dev
curl -s "$BASE/list/test-abcd"                       # -> {} (404)
curl -s -X PUT "$BASE/list/test-abcd" \
  -H 'content-type: application/json' \
  -d '{"words":["école","jeudi"],"updatedAt":1725900000000}'
curl -s "$BASE/list/test-abcd"                       # -> the stored list
```

## Cost & privacy

Free tier: 100k requests/day, 1k KV writes/day — far beyond a family's use.
The only data transmitted is the practice words plus a timestamp. Reflect that
in the Play Console **Data safety** form and your privacy policy (see
[`../PRIVACY.md`](../PRIVACY.md)).
