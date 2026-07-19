// driver.etzhayyim.com — static-asset Worker. All paths fall through to the
// `ASSETS` binding (configured in wrangler.jsonc with
// `not_found_handling: "single-page-application"`).
//
// Two endpoints are handled inline:
//   * GET /health        — liveness probe
//   * GET /_app/meta     — agent metadata (used by the platform's
//                          discovery sweeps)

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
