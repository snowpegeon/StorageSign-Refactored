# StorageSign-Refactored

Spigot/Paper 向け Minecraft プラグインです。  
看板（Sign）に保管するアイテムの種類と数量を記録することで、ラージチェストやシュルカーボックスを「品目専用倉庫」として管理できます。ホッパーや投げ捨てによる自動入出庫、右クリックによる手動ピックアップなど、倉庫整理を自動化・効率化する機能を提供します。

---

## 目次

- [ベース版との差分（snowpegeon/StorageSign 比較）](#ベース版との差分snowpegeonstoragesign-比較)
  - [追加された機能](#追加された機能)
  - [削除・廃止された機能](#削除廃止された機能)
  - [確認観点（漏れ防止）](#確認観点漏れ防止)
- [ユーザー向けガイド](#ユーザー向けガイド)
  - [動作環境](#動作環境)
  - [インストール](#インストール)
  - [StorageSign の作り方](#storagesign-の作り方)
  - [基本的な使い方](#基本的な使い方)
  - [自動入出庫](#自動入出庫)
  - [権限一覧](#権限一覧)
  - [主な設定項目](#主な設定項目)
- [開発者向けガイド](#開発者向けガイド)
  - [プロジェクト構成](#プロジェクト構成)
  - [ビルド手順](#ビルド手順)
  - [テスト](#テスト)
  - [データモデル](#データモデル)
  - [設定の拡張](#設定の拡張)

---

## ベース版との差分（snowpegeon/StorageSign 比較）

この章は、ベース版 [snowpegeon/StorageSign](https://github.com/snowpegeon/StorageSign) と本リポジトリを比較し、機能差分を整理したものです。

### 追加された機能

- `config.yml` に `unregister-on-empty` を追加
  - 保管数が 0 になった際に登録を解除するかを切り替え可能。
- `config.yml` に `item-identifier-aliases` を追加
  - 旧識別子や別名を現在の Material 名にマッピングでき、リネーム耐性を向上。
- `config.yml` に `virtual-item-identifiers` を追加
  - 実在しない識別子を `MATERIAL[:damage]` にマッピング可能。
- 吊り看板系（`_HANGING_SIGN` / `_WALL_HANGING_SIGN`）の取り扱いを実装
  - 隣接判定や向き判定を含め、通常看板と同様に対象化。
- 互換性・可搬性向上のためのレジストリ層を追加
  - `MaterialRegistry` / `LegacyNameRegistry` / `DyeRegistry` により、素材解決と旧名互換を整理。
- テストスイートを追加
  - `StorageSign` 本体とレジストリ・ポーション補助ロジックのユニットテストを整備。

### 削除・廃止された機能

- `plugin.yml` の外部プラグイン依存宣言を廃止
  - 旧版の `depend: [Logger, FarmNBT]` と `softdepend: [WorldGuard]` を削除。
  - `Logger` は外部プラグイン `com.github.teruteru128.logger.Logger` への依存であり、本リファクタリング版では標準 JDK ログ API へ移行したため不要。
  - `FarmNBT` および `WorldGuard` は旧版コードにも実装が存在しない宣言のみの依存であったため、削除しても機能上の影響はない。
- 外部 Logger ライブラリ連携を廃止
  - 旧版の `com.github.teruteru128.logger.Logger` 前提から、`java.util.logging.Logger`（JDK 標準）ベースへ移行。
- ビルド時の `project.properties` + `maven-resources-plugin` コピー運用を廃止
  - 現行は `maven-shade-plugin` によるパッケージング中心へ移行。

注: ここでの「削除・廃止」は、主に公開設定・依存宣言・実装構成として確認できる差分を示します。
内部実装の微細な最適化差分は随時更新されるため、必要に応じてコード比較を再実施してください。

---

## ユーザー向けガイド

### 動作環境

| 項目 | 要件 |
|---|---|
| サーバーソフト | Spigot 1.21.4 以降 / Paper 1.21.4 以降 |
| Java | 21 以降 |

### インストール

1. [Releases](../../releases) から最新の `.jar` ファイルをダウンロードします。
2. サーバーの `plugins/` フォルダに配置します。
3. サーバーを起動すると `plugins/StorageSign-Refactored/config.yml` が生成されます。

### StorageSign の作り方

デフォルトのクラフトレシピ:

```
[チェスト] [看板]  [チェスト]
[看板]     [チェスト] [看板]
[チェスト] [看板]  [チェスト]
```

`config.yml` の `hardrecipe: true` にするとチェストの 1 つがエンダーチェストに変わります。

### 基本的な使い方

#### StorageSign を看板に設置する

1. StorageSign アイテムを手に持ちます。
2. チェストやシュルカーボックスなどの収納ブロックに隣接する壁またはブロックに看板を設置します。
3. 看板を設置した瞬間に、隣接するコンテナの中身に基づいてアイテム種別と数量が自動で記録されます。

#### 手動インポート（右クリック）

- 登録されたアイテムを手に持って StorageSign を右クリックすると、手持ちアイテムが隣接コンテナに格納されます。
- スニーク（Shift）＋右クリックでスタック単位の格納も可能です。

#### 手動エクスポート（右クリック）

- 空手で StorageSign を右クリックすると、隣接コンテナからアイテムを取り出します。
- 取り出す量は `divide-limit` / `sneak-divide-limit` で制御できます。

### 自動入出庫

| 機能 | 説明 | 設定キー |
|---|---|---|
| 自動インポート | 搬送ブロック（ホッパー/ドロッパー/ディスペンサー/クラフター/ホッパー付きトロッコ/チェスト付きトロッコ/チェスト付きボート）がアイテムを押し込むと StorageSign 経由でコンテナへ自動格納 | `auto-import` |
| 自動エクスポート | 搬送ブロック（ホッパー/ドロッパー/ディスペンサー/クラフター/ホッパー付きトロッコ/チェスト付きトロッコ/チェスト付きボート）がアイテムを引き出す・排出すると StorageSign 経由で在庫同期 | `auto-export` |
| 自動収集 | 登録アイテムを手に持った状態でドロップアイテムに触れると自動回収 | `autocollect` |

### 権限一覧

| 権限ノード | デフォルト | 説明 |
|---|---|---|
| `storagesign.*` | OP | すべての権限を付与 |
| `storagesign.use` | 全員 | StorageSign のインタラクション |
| `storagesign.craft` | 全員 | クラフトの許可 |
| `storagesign.place` | 全員 | 看板としての設置 |
| `storagesign.break` | 全員 | StorageSign ブロックの破壊 |
| `storagesign.create` | OP | 看板編集による新規作成 |
| `storagesign.autocollect` | 全員 | 自動収集 |

### 主な設定項目

`plugins/StorageSign-Refactored/config.yml` で変更できます。

| キー | デフォルト | 説明 |
|---|---|---|
| `manual-import` | `true` | 手動インポートの有効化 |
| `manual-export` | `true` | 手動エクスポートの有効化 |
| `auto-import` | `true` | 自動インポート（搬送ブロック対応）の有効化 |
| `auto-export` | `true` | 自動エクスポート（搬送ブロック対応）の有効化 |
| `autocollect` | `true` | ドロップアイテム自動収集の有効化 |
| `hardrecipe` | `false` | 難易度の高いクラフトレシピを使用 |
| `divide-limit` | `345600` | 右クリック 1 回で取り出す最大アイテム数 |
| `sneak-divide-limit` | `34560` | スニーク右クリック 1 回で取り出す最大アイテム数 |
| `max-stack-size` | `16` | StorageSign アイテムのスタック上限 |
| `unregister-on-empty` | `false` | 残数が 0 になったときに登録を解除するか |
| `no-bud` | `false` | BUD パルスによる看板破壊を防止する |

---

## 開発者向けガイド

### プロジェクト構成

```
src/
├── main/java/storagesign/
│   ├── StorageSign.java          # データモデル（イミュータブル）
│   ├── StorageSignCore.java      # プラグインメインクラス
│   ├── ConfigLoader.java         # config.yml のロード
│   ├── config/
│   │   └── StorageSignNBTConfig.java  # NBT 永続データ管理
│   ├── item/
│   │   ├── EnchantHelper.java         # エンチャント本の識別子処理
│   │   ├── OminousBottleHelper.java   # 不吉なビンの識別子処理
│   │   ├── PotionHelper.java          # ポーション系の識別子処理
│   │   └── SpecialCaseItemSupport.java # 特殊アイテムの分岐ロジック
│   ├── listener/                 # イベントリスナー群
│   ├── registry/
│   │   ├── DyeRegistry.java          # 染料の互換名マッピング
│   │   ├── LegacyNameRegistry.java   # レガシー識別子の解決
│   │   └── MaterialRegistry.java     # Material の検索・解決
│   └── task/
│       └── ExportSignTask.java        # 非同期エクスポートタスク
└── test/java/storagesign/        # 単体テスト
```

### ビルド手順

**必要なもの**

- JDK 21 以降
- Maven 3.8 以降

```bash
# 依存関係の解決とビルド
mvn package

# 生成される jar（shade 済み）
target/StorageSign-Refactored-<version>.jar
```

### テスト

```bash
mvn test
```

テストは `src/test/java/storagesign/` にあります。サーバー API に依存しないユニットテストのみ含まれており、Spigot/Paper のモックは不要です。

### データモデル

`StorageSign` クラスは看板ブロックおよびインベントリアイテム両方の表現を持つイミュータブルな値オブジェクトです。

**看板ブロックのテキスト形式（4 行）**

```
行 0: "StorageSign"
行 1: アイテム識別子（例: STONE, POTION:HEAL:0）
行 2: 保管数量（数値文字列）
行 3: サマリー（"LC/スタック/個"）
```

**アイテム識別子の形式**

| 種別 | 形式例 |
|---|---|
| 通常アイテム | `STONE` / `STONE:0` |
| ポーション | `POTION:HEAL:0` |
| スプラッシュポーション | `SPOTION:REGEN:1` |
| 残留ポーション | `LPOTION:HEAL:2` |
| エンチャント本 | `ENCHBOOK:sharpness:5` |
| 不吉なビン | `OMINOUS_BOTTLE:2` |

### 設定の拡張

`config.yml` の以下のテーブルをコードを変更せずに拡張できます。

**`item-identifier-aliases`**  
識別子の別名を現行の Material 名にマッピングします。MC のリネームや旧データの移行に使用します。

```yaml
item-identifier-aliases:
  SIGN: OAK_SIGN
  MY_OLD_NAME: NEW_MATERIAL_NAME
```

**`virtual-item-identifiers`**  
実在しない識別子をバックエンドの `MATERIAL[:damage]` にマッピングします。  
サーバー独自の仮想アイテムや旧マーカーの移行に使用します。

```yaml
virtual-item-identifiers:
  EmptySign: OAK_SIGN:1
  HorseEgg: END_PORTAL:1
```
