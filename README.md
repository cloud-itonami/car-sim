# car-sim

**`cloud-itonami/car-sim` は soft-body カーシミュレータの *配信面* であって、
シミュレータの実装ではない。** 中身は `etzhayyim/root` から切り出された
Cloudflare appview 一式（元 SvelteKit、2026-08-26 に ClojureScript
（reagent + re-frame + jp-go-dds）へ移行 — 下記参照、+ 事前ビルド済み wasm）で、
物理も描画も上流の別リポジトリが所有する。

そして **2026-08-12 現在、この配信面は動かない。** チェックインされている
`kami_app_car_sim_bg.wasm` をブラウザが受理しない（`WebAssembly.validate` が
`false`）。実測手順と生の出力は `docs/operator-quickstart.md`、判断は
`docs/adr/0001-what-this-repo-can-and-cannot-run.md`。

- GitHub: `cloud-itonami/car-sim` ／ west name: **`car-sim`** ／ path: `orgs/cloud-itonami/car-sim`
- 宣言上の controller DID: `did:web:driver.etzhayyim.com`（**このホストは今日 DNS に無い**）
- `README.edn` の `:name` は `com-etzhayyim-app-car-sim` のまま止まっている（切り出し前の名前）。
  現在の identity は上の 1 行目が正しい。

## 何が入っているか — 面は 2 つあり、**どちらも今日はデモを配れない**

**2026-08-26 追記: フロントエンドを SvelteKit から ClojureScript に移行した。**
`appview/…/svelte/` は削除し、`appview/…/cljs/`（reagent + re-frame + jp-go-dds）に
置き換えた。中身は変わらず「切り出し時のスキャフォールドページ」で、これは
デモ（下表 1 行目）そのものではない。以下の表と本節の記述はこの移行後の状態。

| 面 | 中身 | 実測した状態 |
|---|---|---|
| **`appview/…/public/`** | 本物のデモ。`index.html`（HUD・キー操作・エンジン音）+ wasm-bindgen の glue + `kami_app_car_sim_bg.wasm`（182,192 B） | **静的配信はできるが、wasm がブラウザに拒否される**。HUD は `—` のまま、canvas は 300×150（既定値）から動かない。**この移行では未変更** |
| **`appview/…/cljs/`** | reagent + re-frame + jp-go-dds のスキャフォールド情報ページ（旧 `svelte/src/routes/+page.svelte` の忠実な移植） | **ビルドは通る**（下記「ビルドしてみる」参照）。ただし旧 `svelte/` 同様、**成果物にデモが 1 バイトも入らない**（そもそも同じスキャフォールドの移植であって、デモを配る面ではない） |

`wrangler.jsonc` はこの移行で `main` を `src/app.ts` に、`assets.directory` を
`./cljs/public` に向け直した。**`src/app.ts` は今 `wrangler.jsonc` の実エントリ**
（`/health` と `/_app/meta` を処理し、それ以外は `env.ASSETS.fetch` で
`./cljs/public` のスキャフォールドページへ委譲する static-asset Worker）。
`public/`（本物のデモ）は依然どのビルド/デプロイ手順からも参照されていない
— デモの配線とリバイバルは decision 4（下記）の別スコープのまま。

旧 `svelte/` が持っていた XRPC BFF プロキシ（`/xrpc/[...path]`、
`AGENTGATEWAY_MCP_ROUTER_URL` へ転送）は、SvelteKit 固有のバックエンドロジックで
あり今回のフロントエンド移行の対象外だったため、**削除せず**
`appview/etzhayyim-wasm-car-sim-c4r51m00/src/xrpc-proxy.ts` へ**そのまま**
（byte-for-byte）退避した。`./$types` 等 SvelteKit 依存の import が残っており
このままでは単体で動かず、`src/app.ts` からも呼ばれていない
（inert な参照実装）。配線するかどうかは ADR 0001 の open question 4 として
残っている。

## wasm が壊れている — GPU の問題ではない

Chrome 151 で `public/index.html` を開くと、ページは読めるがここで止まる:

```
WebAssembly.instantiateStreaming(): opcode unreachable is not allowed
in constant expressions @+165717
```

**同じ実行の中で `navigator.gpu.requestAdapter()` はアダプタを返している。**
WebGPU の有無・ヘッドレスかどうか・サーバの MIME（`application/wasm` で配信済み）
のいずれでもなく、**モジュールそのものが受理されていない**。

切り出しは無実である —— 18 ファイル・280,083 バイトが `migration.edn` の記録と
バイト単位で一致する。この壊れた wasm は `etzhayyim/root` から**壊れたまま来た**。

## 直す先はここではない（Rust を書き直さない）

この wasm を吐いた Rust crate `kami-app-car-sim` は**上流に既に存在しない** ——
`kotoba-lang/kami-engine` の PR #82「Remove Rust workspace」で削除され、論理は
portable Clojure に移されている（各 repo の README より）:

| 行き先 | 何を所有するか |
|---|---|
| `kotoba-lang/kami-app-car-sim` | garage / ground / boot-config / controls（pure Clojure、ADR-2607010000） |
| `kotoba-lang/kami-vehicle` | node + beam + XPBD + Pacejka タイヤ（zero-dep CLJC、ADR-2607010930） |
| `kotoba-lang/kami-vehicle-scene` | 車両・地面の EDN オーサリング面 |

superproject の規則もこちらを向いている: 3D は kami-engine stack（WebGPU first /
WebGL 2.0 fallback）を使い、**個別 app の描画のために新しい Rust crate を書かない**。
したがって「壊れた wasm をビルドし直す」は選択肢に入らない。**このリポジトリの
仕事は、その CLJC 実装を配る面になること**であって、Rust 成果物の修復ではない。

## HUD の契約と、繋がっていない 2 つの操作

wasm が読む window グローバルは `__carsim_{throttle,brake,handbrake,steer,gear}`、
書くのは `__carsim_hud`（`fps` / `speed_kmh` / `rpm` / `gear` / `grounded_wheels` /
`broken_beams` / `nodes` / `beams`）。`index.html` はこれに一致している。

**一致していないものが 2 つある**（wasm のデータ領域に文字列が 1 件も無い）:

- **☀ time スライダ** → `window.__carsim_time_of_day` は誰も読まない
- **weather セレクト** → `window.__carsim_weather` も同じ

wasm を直しても、この 2 つは動かない。UI にあるので「効いている」と読まれる。

## 使いはじめる

**`docs/operator-quickstart.md`** —— 実際に踏んだ手順と実測値だけが書いてある
（踏めなかったものは「踏めない」と書いてある）。読む順は quickstart →
`docs/adr/0001-…` → `appview/…/kotodama.jsonld`（宣言された identity と route）。

## 隣との境界

| 混同しやすい相手 | 違い |
|---|---|
| `kotoba-lang/kami-vehicle` | soft-body 物理の**実装**。ここはその成果物を配る面 |
| `kotoba-lang/kami-app-car-sim` | 車両・地面・起動設定の**ドメイン層**。ここには 1 行も無い |
| `cloud-itonami/cad` | 同じ切り出し世代の appview。あちらは `kotoba/` が動く。ここは動く面が無い |

## ライセンス

Apache-2.0 + etzhayyim Charter Compliance Rider v3.1（`NOTICE` を参照）。
