(ns car-sim.app
  "car-sim appview frontend shell.

  Migrated from the SvelteKit scaffold at
  appview/etzhayyim-wasm-car-sim-c4r51m00/svelte/src/routes/+page.svelte (a
  single `+page.svelte`, ~84 lines: a self-describing info panel that reports
  this appview's own title/name/kind, its declared public routes, its
  declared wrangler vars, and whether an XRPC route is configured — plus a
  `<svelte:head>` that sets the document title to the app name) to reagent +
  re-frame, rendered with `jp-go-dds.core` (デジタル庁デザインシステム)
  hiccup. Every section, every conditional branch, and every displayed value
  from the original component is preserved below — nothing was invented and
  nothing was dropped. The only change is presentational: the original
  `<style>` block hand-rolled a dark theme with raw hex colors
  (`#11161d` / `#22c55e` etc. — none of that palette survives here); this
  namespace instead expresses the same 3 information sections + list/chip
  layout using jp-go-dds's `dds-ext-*` layout classes and `dads-*` token
  classes, per this workspace's repo-wide DADS-first UI rule (skill
  `kotoba-uiux`) — app-authored raw-hex CSS was not carried forward.

  This is a scaffold info page, not the car-sim demo itself. The actual
  soft-body car demo (wasm + glue JS + HUD) lives in the sibling
  `appview/etzhayyim-wasm-car-sim-c4r51m00/public/` directory, is untouched
  by this migration, and is documented (broken wasm, HUD contract, open
  questions) in the repo root README.md and docs/adr/0001-*.md — see those,
  not this namespace, for that surface.

  The SvelteKit route this replaces also hosted a backend XRPC BFF proxy
  (`svelte/src/routes/xrpc/[...path]/+server.ts`, POST /xrpc/:nsid ->
  AGENTGATEWAY_MCP_ROUTER_URL). That is backend/server logic, not a Svelte
  *view*, so it was not ported here and re-frame (a browser-only app) could
  not run it anyway. It was relocated verbatim (not rewritten) to
  `appview/etzhayyim-wasm-car-sim-c4r51m00/src/xrpc-proxy.ts` so deleting
  `svelte/` does not destroy the only copy of that logic; see that file's
  header for its current (unwired) status.

  `public/index.html`'s inlined <style> was produced once, at authoring
  time, by `jp-go-dds.page/->page` running on the JVM (via this deps.edn's
  jp-go-dds git/sha), concatenating the vendored `dds.css` with
  `jp-go-dds.core/ext-css` — exactly what `jp-go-dds.page/page` composes for
  its own <style> block. This namespace itself only requires
  `jp-go-dds.core` — the browser bundle does not need `jp-go-dds.page` or
  `html.core` at runtime; those are JVM-only tools used to author the static
  shell once. Regenerate that shell (e.g. if jp-go-dds's core components or
  ext-rules change) with:

    (require '[jp-go-dds.page :as page] '[clojure.java.io :as io])
    (spit \"public/index.html\"
          (page/->page {:title \"etzhayyim-wasm-car-sim-c4r51m00\"
                         :description \"car-sim appview frontend shell (reagent + re-frame + jp-go-dds).\"
                         :css (slurp (io/resource \"jp_go_dds/dds.css\"))}
                        [:div {:id \"app\"}]
                        [:script {:src \"js/app.js\"}]))"
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [jp-go-dds.core :as dds]))

;; --- state ------------------------------------------------------------------

(def default-db
  "Every field the original +page.svelte's `app` script-literal held (title,
  project, name, kind, routeCount, routes, vars, xrpc, relativePath), held as
  re-frame app-db data instead of a component-local const, so there is real
  event/sub logic to test. Values are unchanged from the Svelte source
  except `:page/relative-path`, which now names this namespace's own file —
  the field's meaning (\"where is this page's own source\") is preserved,
  its value simply follows the file it describes."
  {:page/title "Car Sim C4r51m00"
   :page/project "etzhayyim-project-car-sim"
   :page/name "etzhayyim-wasm-car-sim-c4r51m00"
   :page/kind "appview"
   :page/route-count 2
   :page/routes ["c4r51m00.etzhayyim.com/*" "driver.etzhayyim.com/*"]
   :page/vars ["AGENTGATEWAY_MCP_ROUTER_URL" "APP_ACTOR_HANDLE" "APP_CAPABILITIES"
               "APP_DESCRIPTION" "APP_DISPLAY_NAME" "APP_EMBED_URL" "APP_FRAMEWORK"
               "APP_NANOID" "APP_PERFORMER_TYPE" "APP_UI_TYPE"]
   :page/xrpc? true
   :page/relative-path "appview/etzhayyim-wasm-car-sim-c4r51m00/cljs/src/car_sim/app.cljs"})

(rf/reg-event-db
 :initialize-db
 (fn [_ _] default-db))

(rf/reg-sub :page/title (fn [db _] (:page/title db)))
(rf/reg-sub :page/project (fn [db _] (:page/project db)))
(rf/reg-sub :page/name (fn [db _] (:page/name db)))
(rf/reg-sub :page/kind (fn [db _] (:page/kind db)))
(rf/reg-sub :page/route-count (fn [db _] (:page/route-count db)))
(rf/reg-sub :page/routes (fn [db _] (:page/routes db)))
(rf/reg-sub :page/vars (fn [db _] (:page/vars db)))
(rf/reg-sub :page/xrpc? (fn [db _] (:page/xrpc? db)))
(rf/reg-sub :page/relative-path (fn [db _] (:page/relative-path db)))

;; --- view ---------------------------------------------------------------

(defn- top-section
  "Mirrors the original `<section class=\"top\">`: a small uppercase
  'Cloudflare {kind}' label, the h1 title, and the monospace app name."
  []
  [:section {:class "dds-ext-section dds-ext-stack"}
   [:p {:class "dds-u-dns-14B-120"} (str "Cloudflare " @(rf/subscribe [:page/kind]))]
   (dds/heading 1 @(rf/subscribe [:page/title]))
   [:span {:class "dds-u-mono-14N-150"} @(rf/subscribe [:page/name])]])

(defn- facts-section
  "Mirrors the original `<section class=\"facts\">`: 3 cards (Project /
  Routes / XRPC), each a label + value — identical to the 3
  `<div><span>…</span><strong>…</strong></div>` blocks in the Svelte source."
  []
  [:section {:class "dds-ext-section"}
   [:div {:class "dds-ext-grid"}
    (dds/card [:span {:class "dds-u-dns-14N-120"} "Project"]
              [:strong @(rf/subscribe [:page/project])])
    (dds/card [:span {:class "dds-u-dns-14N-120"} "Routes"]
              [:strong (str @(rf/subscribe [:page/route-count]))])
    (dds/card [:span {:class "dds-u-dns-14N-120"} "XRPC"]
              [:strong (if @(rf/subscribe [:page/xrpc?]) "enabled" "not configured")])]])

(defn- routes-panel
  "Mirrors the original `{#if app.routes.length}` / `{:else}` branch under
  'Public Routes': a list when non-empty, the exact same muted fallback
  sentence when empty."
  []
  (let [routes @(rf/subscribe [:page/routes])]
    (dds/card
     (dds/heading 2 "Public Routes")
     (if (seq routes)
       [:ul {:class "dads-list"}
        (for [route routes] ^{:key route} [:li route])]
       [:p {:class "dds-ext-lead"} "No public route is declared next to this app surface."]))))

(defn- vars-panel
  "Mirrors the original `{#if app.vars.length}` / `{:else}` branch under
  'Runtime Bindings': the Svelte source rendered this as `<ul class=\"chips\">`
  — jp-go-dds's `chip-label` component is the DADS equivalent of that chip
  styling."
  []
  (let [vars @(rf/subscribe [:page/vars])]
    (dds/card
     (dds/heading 2 "Runtime Bindings")
     (if (seq vars)
       [:div {:class "dds-ext-row"}
        ;; `^{:key v}` only attaches reader metadata to a *literal* vector
        ;; form at compile time (the idiomatic reagent `for` pattern); it
        ;; would silently no-op on a function-call form like
        ;; `(dds/chip-label v)`. `with-meta` attaches it to the actual
        ;; runtime vector `chip-label` returns, which reagent does read.
        (for [v vars] (with-meta (dds/chip-label v) {:key v}))]
       [:p {:class "dds-ext-lead"} "No public vars are declared in the nearest wrangler config."]))))

(defn- source-panel
  "Mirrors the original `<section class=\"panel path\">`: just the
  relativePath value."
  []
  (dds/card
   (dds/heading 2 "Source")
   [:p {:class "dds-u-mono-14N-150"} @(rf/subscribe [:page/relative-path])]))

(defn app
  []
  [:main {:class "dds-ext-container"}
   [top-section]
   [facts-section]
   [:section {:class "dds-ext-section dds-ext-stack"}
    [routes-panel]
    [vars-panel]
    [source-panel]]])

;; --- mount ------------------------------------------------------------------

(defn render []
  ;; Mirrors the original `<svelte:head><title>{app.name}</title></svelte:head>` —
  ;; that reactively set document.title to the app name; here it is set once
  ;; at mount from the same app-db value.
  (set! (.-title js/document) @(rf/subscribe [:page/name]))
  (rdom/render [app] (.getElementById js/document "app")))

(defn ^:export main []
  (rf/dispatch-sync [:initialize-db])
  (render))
