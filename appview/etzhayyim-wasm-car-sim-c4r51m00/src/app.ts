// driver.etzhayyim.com — static-asset Worker. All paths fall through to the
// `ASSETS` binding (configured in wrangler.jsonc; `not_found_handling` is
// currently "none", not "single-page-application" — the two used to
// describe different configs, per docs/adr/0001-*.md decision 6; this
// comment now states the actual configured value instead of repeating that
// mismatch).
//
// This file is `wrangler.jsonc`'s `main` as of the Svelte→cljs frontend
// migration (previously `main` pointed at the SvelteKit build's
// `_worker.js`, which never actually served this file — see README.md and
// docs/adr/0001-*.md for that history). `ASSETS.fetch` below now serves
// `./cljs/public` (the reagent + re-frame scaffold), not the sibling
// `public/` directory that holds the (still-broken) wasm car-sim demo —
// that demo remains unwired, unaffected by this migration.
//
// Two endpoints are handled inline:
//   * GET /health        — liveness probe
//   * GET /_app/meta     — agent metadata (used by the platform's
//                          discovery sweeps)
//
// NOT handled here: `/xrpc/*`. The SvelteKit app being replaced had an XRPC
// BFF proxy at that path (`svelte/src/routes/xrpc/[...path]/+server.ts`,
// forwarding to AGENTGATEWAY_MCP_ROUTER_URL); it was relocated verbatim
// (not rewritten, not wired in here) to ./xrpc-proxy.ts when `svelte/` was
// deleted, so the only copy of that logic is preserved rather than lost.
// Wiring it into this fetch handler is a follow-up decision, not made by
// this migration — see docs/adr/0001-*.md open question 4.

interface Env {
  ASSETS: { fetch(req: Request): Promise<Response> };
  APP_NANOID?: string;
}

const APP = "car-sim";

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    if (url.pathname === "/health") {
      return json({ ok: true });
    }
    if (url.pathname === "/_app/meta") {
      return json({
        ok: true,
        actor: "did:web:driver.etzhayyim.com",
        nanoid: env.APP_NANOID ?? "c4r51m00",
        execution: "static-asset",
        wasmCrate: "40-engine/kami-engine/kami-app-car-sim",
        physicsCrate: "40-engine/kami-engine/kami-vehicle",
        granularity: { nodes: 84, beams: 220, wheels: 4 },
      });
    }
    return env.ASSETS.fetch(req);
  },
} satisfies ExportedHandler<Env>;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
      "cache-control": "no-store",
    },
  });
}

void APP;
