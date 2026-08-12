# ADR 0001 — car-sim が今日配れるもの、配れないもの

- **Status**: accepted
- **Date**: 2026-08-12
- **Scope**: `cloud-itonami/car-sim`（west name `car-sim`）

## Context

このリポジトリは `etzhayyim/root` の `60-apps/etzhayyim-project-car-sim` から
18 ファイル・280,083 バイトとして切り出された（`migration.edn`）。切り出し自体は
成功している —— 実測した 18 ファイルの合計が記録とバイト単位で一致する。

**問題は、切り出された結果が何であるかを述べる文書が無かったことである。** 入口は
`README.edn`（1 行のメタデータ）だけで、「今このリポジトリで何が動くか」を答えない。
`kotodama.jsonld` と `wrangler.jsonc` は稼働中の demo として自己記述しており、
`src/app.ts` には `/health` が書いてある。**そのどれもが今日の実態ではない。**

実測（手順と生の出力は `docs/operator-quickstart.md`）:

| 測ったもの | 実測 |
|---|---|
| `public/` の静的配信 | 4 資産すべて 200。wasm は `application/wasm`、先頭 4 byte は `\0asm` |
| `WebAssembly.validate(wasm)` | **false**（Chrome 151 / Node v26.3.0 とも） |
| ページを開いたとき | `instantiateStreaming(): opcode unreachable is not allowed in constant expressions @+165717` |
| 同じ実行での `navigator.gpu.requestAdapter()` | **adapter ok**（GPU は問題ではない） |
| 6 秒後の HUD | 全項目 `—`、`window.__carsim_hud` は `undefined`、canvas は 300×150 の既定値のまま |
| `svelte/` の `npm install` → `npm run build` | 通る（92 packages / `✓ built in 7.55s`） |
| ビルド成果物 `.svelte-kit/cloudflare/client` | **17 ファイル、すべて `_app/…`。demo の資産は 0 件** |
| `grep health .svelte-kit/cloudflare/_worker.js` | **0**（`src/app.ts` はエントリではない） |
| `dig driver.etzhayyim.com` / `c4r51m00.etzhayyim.com` | **レコード無し**（`etzhayyim.com` 自体は解決する） |
| wasm 内の `time_of_day` / `weather` | **0 件**（UI の 2 つの操作が何にも繋がっていない） |

つまりこのリポジトリには**動いているふりをする面が 3 つ**ある: 壊れた wasm を指す
demo、demo を含まない deploy 経路、解決しないホストを宣言する route。

## Decision

1. **このリポジトリは配信面であって実装ではない、と明示する。** 物理・車両・地面・
   起動設定の正本は `kotoba-lang/kami-vehicle` / `kami-app-car-sim` /
   `kami-vehicle-scene` にある。ここにドメインコードを足さない。

2. **チェックインされた `kami_app_car_sim_bg.wasm` を「動く成果物」として扱わない。**
   V8 が拒否することを実測した。GPU・ヘッドレス・MIME のいずれでもない。
   壊れたまま上流から来ており（切り出しはバイト一致）、**このリポジトリの中に
   直せる原因は無い。**

3. **この wasm をリビルドしない。** 生成元の Rust crate は
   `kotoba-lang/kami-engine` PR #82「Remove Rust workspace」で削除済みで、論理は
   portable CLJC に移されている（ADR-2607010000 / ADR-2607010930、各 repo の README）。
   加えて superproject の規則は (a) 個別 app の描画のために新しい Rust crate を
   書くことを禁じ、(b) 3D は kami-engine stack（WebGPU first / WebGL 2.0 fallback）を
   使うことを求めている。**「Rust を直す」は規則にも上流の現在地にも反する。**

4. **デモを復活させる作業は「CLJC 実装を配る面を作る」ことと定義する。** 具体的には
   `kami-app-car-sim` + `kami-vehicle` を ClojureScript でブラウザに載せ、描画を
   `webgpu`（`kami.webgpu`）に繋ぐ。既存の `public/index.html` は HUD・入力・
   エンジン音の**契約仕様として読める**（`__carsim_{throttle,brake,handbrake,steer,gear}`
   → `__carsim_hud`）ので、捨てずに参照する。

5. **`public/` が配線されていないことを、設定の書き間違いとして黙って直さない。**
   直す先が (a) `svelte.config.js` の `files.assets` (b) `wrangler.jsonc` の
   `assets.directory` (c) `svelte/static/` へのコピー、のどれであるべきかは
   **決定 4 の結論に従属する** —— 配るものが変わるなら配線先も変わる。壊れた wasm を
   配線し直しても、出荷されるのは「読み込みに失敗するページ」であって改善ではない。

6. **`src/app.ts` を「Worker の実装」として読まない。** `main` は SvelteKit の
   `_worker.js` を指しており、`/health` も `/_app/meta` もビルド成果物に存在しない。
   自身のコメントが `not_found_handling: "single-page-application"` と書いている
   一方 `wrangler.jsonc` の実値は `"none"` で、**別の設定を説明している。**
   このファイルは切り出し前の別経路の残骸として扱う。

## Consequences

- **今日 deploy してはならない。** 配られるのは切り出し時のスキャフォールドページで、
  それを `driver.etzhayyim.com` に出すことは「demo がある」という誤った主張を
  公開することになる（ホストはまだ DNS に無いので、今なら誰も見ていない）。
- テストできる面が無い。ドメインロジックを持たないので、意味のある単体テストを
  ここに足すことはできない —— **テストの行き先も上流である。**
- `README.edn` の `:name` は切り出し前の `com-etzhayyim-app-car-sim` のまま。
  identity は `cloud-itonami/car-sim` が正しいが、`etzhayyim.repository/v1` を
  読む下流が居るかを確かめてから触ること。
- `.gitignore` が無いので、quickstart を踏むと `node_modules` / `.svelte-kit` /
  `package-lock.json` がツリーに残る。quickstart §2 に掃除手順を明記した。

## Open questions（解いていない。次に触る者へ）

1. **上流の wasm も壊れているのか。** `etzhayyim/root@f0f9f2aa67` の同じパスと
   ハッシュを突き合わせれば、いつから壊れていたかが分かる。**壊れた成果物が
   公開 demo として配られていた期間があるなら、それ自体が報告対象。**
2. **独立した wasm 検証系を通していない。** 判定は 2 つとも V8（Chrome / Node）。
   `wasm-tools validate` か wasmtime を通せば「仕様上不正」と言い切れる。今は
   「V8 が拒否する」までしか言えない。
3. **`time_of_day` / `weather` の UI をどうするか。** 現状は wasm 側に受け手が
   無い。決定 4 の再実装で受けるか、UI から外すか。**放置すると「効いている
   ように見える操作」が残る。**
4. **`svelte/` の scaffold ページを残すか。** XRPC BFF（`/xrpc/[...path]`）は
   実装として成立しているが、転送先 `mcp.etzhayyim.com` が解決しない。BFF だけ
   残して page を捨てる選択肢がある。
5. **`kami-engine-audio.js`（9.8 KB）の出自。** `public/` にあるが wasm とは
   独立した手書きの WebAudio 実装に見える。CLJC 化の対象かどうか未判断。
