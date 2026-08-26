// PRESERVED, NOT PORTED, NOT WIRED IN, NOT MODIFIED BELOW THIS COMMENT BLOCK.
//
// This file is the SvelteKit XRPC BFF proxy that used to live at
// `svelte/src/routes/xrpc/[...path]/+server.ts`. When `svelte/` was deleted
// as part of migrating this appview's *frontend* from SvelteKit to
// ClojureScript (reagent + re-frame, see ../cljs/), this backend/server
// logic was out of scope for that migration (it is not a Svelte view, and
// re-frame is a browser-only app that could not run a server-side proxy
// anyway) — "leave backend TypeScript/XRPC code alone" per that migration's
// instructions. Rather than let deleting `svelte/` silently destroy the
// only copy of this proxy, it was relocated here byte-for-byte (everything
// below this comment block is the original `+server.ts` body, unedited).
//
// Consequences of leaving it unedited:
//   - It still `import`s from `@sveltejs/kit` and a SvelteKit-generated
//     `./$types`. Neither resolves now that `svelte/` and its
//     `node_modules`/`.svelte-kit` are gone, so this file does not compile
//     or run standalone as-is.
//   - It is NOT imported by `./app.ts` (the Worker's actual `fetch` entry —
//     see that file's header). A request to `/xrpc/*` on the deployed
//     Worker does nothing today; it falls through to `ASSETS.fetch` like
//     any other unmatched path.
//
// docs/adr/0001-what-this-repo-can-and-cannot-run.md's open question 4
// ("`svelte/` の scaffold ページを残すか… BFF だけ残して page を捨てる選択肢
// がある") is resolved by this migration in exactly that direction: the
// scaffold page is gone (replaced by the cljs one), and the BFF is kept —
// but *wiring* it into a real Cloudflare Worker (re-implementing the
// SvelteKit-specific pieces below in terms of plain `Request`/`Response`,
// then mounting it from `./app.ts`'s fetch handler) is a separate,
// not-yet-made decision, left for whoever picks this up next.
//
// --- original svelte/src/routes/xrpc/[...path]/+server.ts follows, verbatim ---

import { json, type RequestEvent } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

const DEFAULT_MCP_ROUTER_URL = 'https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message';

type Env = Record<string, unknown> & { AGENTGATEWAY_MCP_ROUTER_URL?: string; MCP_ROUTER_URL?: string };

function envOf(event: RequestEvent): Env { return ((event.platform as { env?: Env } | undefined)?.env ?? {}) as Env; }

function mcpRouterUrl(env: Env): string {
  const configured = typeof env.AGENTGATEWAY_MCP_ROUTER_URL === 'string' && env.AGENTGATEWAY_MCP_ROUTER_URL.trim()
    ? env.AGENTGATEWAY_MCP_ROUTER_URL
    : typeof env.MCP_ROUTER_URL === 'string' && env.MCP_ROUTER_URL.trim()
      ? env.MCP_ROUTER_URL
      : DEFAULT_MCP_ROUTER_URL;
  return configured.replace(/\/+$/, '');
}

function noStore(body: unknown, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set('cache-control', 'no-store');
  return json(body, { ...init, headers });
}

export const POST: RequestHandler = async (event) => {
  const nsid = event.params.path;
  if (!nsid) return noStore({ error: 'Missing XRPC method' }, { status: 400 });
  const input = await event.request.json().catch(() => ({}));
  const headers = new Headers(event.request.headers);
  headers.delete('host');
  headers.set('content-type', 'application/json');
  headers.set('x-etzhayyim-bff', 'sveltekit-edge-bff');
  headers.set('x-etzhayyim-xrpc-method', nsid);
  const upstream = await fetch(mcpRouterUrl(envOf(event)), {
    method: 'POST',
    headers,
    body: JSON.stringify({ jsonrpc: '2.0', id: crypto.randomUUID(), method: 'tools/call', params: { name: nsid, arguments: input } })
  });
  const upstreamText = await upstream.text();
  let payload: unknown = upstreamText;
  try { payload = upstreamText ? JSON.parse(upstreamText) : null; } catch { /* Preserve text payload. */ }
  if (!upstream.ok) return noStore({ error: 'MCP router request failed', upstream: payload }, { status: upstream.status });
  if (payload && typeof payload === 'object' && 'error' in payload) {
    const error = (payload as { error?: { message?: string } }).error;
    return noStore({ error: error?.message ?? 'MCP router returned an error', upstream: payload }, { status: 502 });
  }
  const result = payload && typeof payload === 'object' && 'result' in payload ? (payload as { result?: unknown }).result : payload;
  const structured = result && typeof result === 'object' && 'structuredContent' in result ? (result as { structuredContent?: unknown }).structuredContent : result;
  return noStore(structured ?? {});
};

export const OPTIONS: RequestHandler = async () => new Response(null, {
  status: 204,
  headers: {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'POST,OPTIONS',
    'access-control-allow-headers': 'content-type,authorization',
    'access-control-max-age': '86400'
  }
});
