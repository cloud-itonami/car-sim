# operator quickstart — car-sim

**この文書の手順はすべて実際に踏んである。** 出力は実測値であって期待値ではない。
§1/§3/§4 を測ったのは 2026-08-12、macOS 26.3.1（darwin 25.3.0 / arm64）、
Node v26.3.0、Google Chrome 151.0.7922.76、playwright 1.59.0。**§2 は
2026-08-26 に、フロントエンドを SvelteKit から ClojureScript（reagent +
re-frame + jp-go-dds）へ移行した際に測り直した**（同じ Node バージョン系列、
`npm`/`shadow-cljs` の版は本文中に記載）。

**結論を先に書く: 今日このリポジトリからデモを立ち上げることはできない。**
以下は「どこまで行けて、どこで止まるか」を再現する手順である。

---

## 0. 取得

```bash
git clone git@github.com:cloud-itonami/car-sim.git
cd car-sim
```

west 管理下（superproject）なら checkout は `orgs/cloud-itonami/car-sim`、
west name は **`car-sim`**:

```bash
west update --fetch smart car-sim
```

前提は Node（測ったのは v26.3.0）とブラウザだけ。§1 はネットワーク不要。

---

## 1. デモを配ってみる —— 4 資産は配れる。wasm が受理されない

```bash
cd appview/etzhayyim-wasm-car-sim-c4r51m00/public
python3 -m http.server 8791
```

**実測**（別ターミナルから）:

```
GET /index.html                 200  text/html         9998 bytes
GET /kami_app_car_sim.js        200  text/javascript  61116 bytes
GET /kami_app_car_sim_bg.wasm   200  application/wasm 182192 bytes   先頭 4 byte = 00 61 73 6d
GET /kami-engine-audio.js       200  text/javascript   9786 bytes
```

配信面は健全（MIME も含めて）。ここまでは問題ない。

`http://127.0.0.1:8791/index.html` をブラウザで開くと、**HUD とパネルは出るが
車は出ない。** コンソールに出るのはこれ:

```
WebAssembly.instantiateStreaming(): opcode unreachable is not allowed
in constant expressions @+165717
```

同じページで 6 秒待ってから DOM を読んだ実測値:

| 読んだもの | 実測 |
|---|---|
| `document.title` | `Car Sim — kami-vehicle soft-body demo` |
| `#h-backend` / `#h-fps` / `#h-speed` / `#h-counts` | `—` / `—` / `— km/h` / `— / —` |
| `window.__carsim_hud` | `undefined`（wasm が一度も書いていない） |
| `window.__carsim_throttle` | `0`（HTML 側の初期化は効いている） |
| `#gc` の `width` × `height` | `300` × `150` ← **HTML canvas の既定値。レンダラが触っていない** |
| `navigator.gpu.requestAdapter()` | **adapter ok** |

**最後の行が重要**: WebGPU は取れている。**GPU の問題でも、ヘッドレスの問題でも、
MIME の問題でもない。** モジュールそのものが受理されていない。

### wasm 単体で確かめる（ブラウザ不要）

```bash
cd appview/etzhayyim-wasm-car-sim-c4r51m00/public
node -e "
const b=require('fs').readFileSync('kami_app_car_sim_bg.wasm');
console.log('validate:', WebAssembly.validate(b));
WebAssembly.compile(b).catch(e=>console.log('compile:', e.message));"
```

**実測**:

```
validate: false
compile: WebAssembly.compile(): opcode unreachable is not allowed in constant expressions @+165717
```

同じ判定がブラウザ（Chrome 151）と Node v26.3.0 の両方で出る。

> **正直な限界**: どちらも V8 である。手元に独立した実装（wasmtime / wasm-tools /
> SpiderMonkey）が無かったので、**「仕様上不正である」ことまでは証明していない。**
> 証明したのは「V8 が拒否する」ことで、Chrome が主要な配信先である以上こちらが
> 運用上の事実になる。独立検証をするなら wasm-tools を入れて
> `wasm-tools validate` を通すこと。

自分でバイト列を歩くと、拒否位置と整合する食い違いが見える（参考、断定ではない）:
オフセット 165717 のバイトは `0x00` で、次の active データセグメントの頭
（`00 41 bc 99 c0 00 0b`）は 165724 から始まる —— 手前のセグメントの宣言長が
7 バイト短い。同様にデータセクションの宣言上の終端（181919）は、実際に続く
`producers` カスタムセクション（181927）の 8 バイト手前に落ちる。
末尾の `target_features` は `sign-ext` / `reference-types` / `multivalue` /
`bulk-memory-opt` / `call-indirect-overlong` だけで、**未対応機能を使っている
わけではない。**

### 切り出しは無実である

**リポジトリのルートで**（`git ls-files` はサブディレクトリでは、そのサブツリー
だけを返す）:

```bash
cd "$(git rev-parse --show-toplevel)"
python3 - <<'PY'
import subprocess, os
files = subprocess.check_output(['git','ls-files'], text=True).split()
orig = [f for f in files if f not in {'README.edn','migration.edn'}]
print(len(orig), 'files', sum(os.path.getsize(f) for f in orig), 'bytes')
PY
```

**実測**: `18 files 280083 bytes` —— `migration.edn` の
`:tracked-files 18 :bytes 280083` とバイト単位で一致する。**この wasm は
`etzhayyim/root` から壊れたまま来た。** 抽出で壊れたのではない。

---

## 2. フロントエンドをビルドしてみる —— 通る。ただしこれもデモではない

**2026-08-26 追記: この節は元 SvelteKit 手順を、フロントエンド移行後の cljs
手順に書き換えたもの。** `appview/…/svelte/` は削除済み、
`appview/…/cljs/`（reagent + re-frame + jp-go-dds）に置き換わっている。
以前の SvelteKit 実測（92 packages / `✓ built in 7.55s` / 成果物 17 ファイル /
`public/` 未配線）は歴史的事実として ADR 0001 に残るが、**このディレクトリでは
再現できない**（`svelte/` 自体が無い）。

```bash
cd appview/etzhayyim-wasm-car-sim-c4r51m00/cljs
npm install
```

**実測**（2026-08-26、この移行時）: `added 129 packages, and audited 130
packages in 21s`。`6 low severity vulnerabilities`（`npm audit fix --force`
待ち、未対応）。`workspace:` のような解決不能な依存は無い。

ビルドは **resource-guard 経由で**（superproject の規則。同時 build を 1 本に絞る）:

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs run build -- amu compile --target wasm32-browser app
```

**実測**: `[:app] Build completed. (111 files, 110 compiled, 0 warnings, 46.92s)`。
`public/js/` に出力される。

テストビルド + 実行:

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs run build -- amu compile --target wasm32-browser test
node out/tests.js
```

**実測**: `car-sim.app-test` の 3 deftest が緑（default-db の値、routeCount と
routes 件数の一致、re-frame の sub/event 往復）。resource-guard は他 repo の
並行ビルドと 1 本のロックを共有するため `exit 2`（lock held）が複数回出るのが
通常 —— このリポジトリの build 自体の失敗ではない。

このビルドが配るのは(旧 `svelte/` 同様)**切り出し時のスキャフォールド
ページの cljs 版**であって、`public/` の soft-body カーシミュレータ demo では
ない。`wrangler.jsonc` の `assets.directory` は今 `./cljs/public` を指す
（移行前は `svelte/.svelte-kit/cloudflare/client`）。

`src/app.ts`（`/health` と `/_app/meta` を実装している Worker）は、この移行で
`wrangler.jsonc` の `main` として**実際にエントリになった**（移行前は SvelteKit の
`_worker.js` がエントリで、`src/app.ts` は死んだコードだった —— 当時の実測は
ADR 0001 に残す）。同ファイル冒頭のコメントの `not_found_handling` 記述も
実値 `"none"` に合わせて修正済み。

### 後片付け（`.gitignore` が無いので必ずやる。ただし `cljs/.gitignore` は追加済み）

```bash
cd appview/etzhayyim-wasm-car-sim-c4r51m00/cljs
rm -rf node_modules .shadow-cljs .cpcache public/js out
cd ../../..
git status --porcelain     # ← 空になること（cljs/.gitignore がこれらを既に除外）
```

リポジトリルート自体には引き続き `.gitignore` が無いが、`cljs/.gitignore` を
この移行で追加したので、cljs 側のビルド生成物は掃除すれば tree に残らない。

---

## 3. 踏めない手順

### deploy —— route のホストが DNS に無い

```bash
for h in etzhayyim.com driver.etzhayyim.com c4r51m00.etzhayyim.com mcp.etzhayyim.com; do
  printf '%-26s ' "$h"; dig +short "$h" | head -2 | tr '\n' ' '; echo
done
```

**実測**:

```
etzhayyim.com              172.67.179.128 104.21.51.111
driver.etzhayyim.com
c4r51m00.etzhayyim.com
mcp.etzhayyim.com
```

ゾーンは生きているが、`wrangler.jsonc` の `routes` が宣言する 2 ホストは
**どちらもレコードが無い**。`kotodama.jsonld` の `@id`
（`did:web:driver.etzhayyim.com`）も現時点では解決しない。XRPC BFF が転送先に
する `mcp.etzhayyim.com` も同様なので、`/xrpc/*` は経路として成立しない。

**deploy は試していない。** 配るものが（§2 のとおり）デモではない以上、
出荷して確かめる価値のある状態にまだ無い。

### wasm のリビルド —— ソースが上流に無い

`kotodama.jsonld` は `wasmCrate: 40-engine/kami-engine/kami-app-car-sim` /
`physicsCrate: 40-engine/kami-engine/kami-vehicle` を指すが、この Rust
workspace は `kotoba-lang/kami-engine` の PR #82 で削除されている（`kami-vehicle`
README より）。論理は portable CLJC に移っており、行き先は
`kotoba-lang/kami-app-car-sim` / `kami-vehicle` / `kami-vehicle-scene`。
**壊れた wasm を作り直す経路は無い**（そして superproject の規則は新しい Rust を
禁じている）。

---

## 4. 効いていない UI —— wasm を直しても動かない 2 つ

`index.html` の上部にある **☀ time スライダ**と **weather セレクト**は、
`window.__carsim_time_of_day` / `window.__carsim_weather` を書く。
wasm のデータ領域にはこの 2 つの文字列が **1 件も無い**:

```bash
cd appview/etzhayyim-wasm-car-sim-c4r51m00/public
for s in __carsim_throttle __carsim_hud time_of_day weather; do
  printf '%-20s %s\n' "$s" "$(grep -c -a -- "$s" kami_app_car_sim_bg.wasm)"
done
```

**実測**: `__carsim_throttle 1` / `__carsim_hud 1` / `time_of_day 0` / `weather 0`。

読まれている契約は `__carsim_{throttle,brake,handbrake,steer,gear}` →
`__carsim_hud` の一方向だけ。`index.html` が fps 表示で最初に見る
`window.__kami_hud_car_sim` も wasm に無い（`??` の後段が拾うので実害は無い）。

> バイナリ内の文字列検索なので、実行時に組み立てられる名前は取り逃がしうる。
> ただし読まれている 5 つは 1 本の連続したリテラルとして実在しており、
> **同じ場所に time/weather が無い**という非対称は十分に強い。

---

## 5. この文書を疑うとき

- **wasm が通る（`validate: true`）** — 誰かが差し替えた。この文書のほうが古い。
  §1 の実測値と `git log -- appview/**/public/*.wasm` を見比べること。
- **`cljs/public/js/` にビルド成果物が無い** — `npm install` を `cljs/` 側で
  やっていない、または shadow-cljs の `:app` build が失敗している可能性。
  §2 の resource-guard 実測（`111 files, 110 compiled, 0 warnings`）と比較。
- **`wrangler.jsonc` の `assets.directory` が `./cljs/public` を指していない** —
  `public/` を配線した変更（decision 4 のデモ復活作業）が入った可能性。
- **`driver.etzhayyim.com` が解決する** — deploy されたということ。§3 は無効になる。
- **`svelte/` というディレクトリが復活している** — 2026-08-26 のフロントエンド
  移行が revert された。§2 の cljs 手順ではなく、この文書の旧版
  （git 履歴、この日付より前のコミット）を見ること。
- **`npm install` が `EUNSUPPORTEDPROTOCOL` で落ちる** — `appview/etzhayyim-wasm-car-sim-c4r51m00/cljs/`
  ではなく別の appview に居る。ここの `package.json` に `workspace:` 依存は無い。
