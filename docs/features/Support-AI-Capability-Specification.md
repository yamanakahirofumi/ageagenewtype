# サポートAI能力（Capability）拡張仕様書

本ドキュメントでは、サポートAIがワークフロー選択や情報収集フェーズにおいて、セキュリティ制限の確認、ワークフロー情報の取得、ファイル有無の調査などを自律的・拡張的に実施できるようにするための、サポートAI能力（Capability）インターフェースの設計と拡張仕様を定義します。

---

## 1. 概要

### 背景と目的
サポートAIは、利用者の初期入力を分析し、最適なワークフローの決定や必要なワークスペース情報の事前取得を行う重要な役割を持ちます。このサポートAIの拡張性を高めるため、セキュリティチェックやワークフロー一覧の取得、ファイルの特定などの機能を汎用的なインターフェース（Capability）として抽象化し、動的に登録・実行可能な仕組み（口）を導入します。

これにより、将来的にWebアクセス検査やOSコマンド検証などの新しい機能・ツールが追加された際、サポートAIの評価ロジックを変更することなく、Capabilityを登録するだけでサポートAIが自律的にそれらの能力を利用できるようになります。

### 基本設計方針
- **統一インターフェース（`SupportAICapability`）**: すべての機能・ツールを `getId()` および `execute(String)` を持つ規格化されたインターフェースとして表現します。
- **動的レジストリ（`SupportAiService`）**: 実行時にCapabilityを動的に登録（`registerCapability`）・解除（`unregisterCapability`）できるようにし、依存関係を粗結合にします。
- **単一の呼び出しポート（口）**: サポートAI（または関連コーディネーター）は、`SupportAiService.getInstance().invoke(capabilityId, argument)` という単一の口を通じて、任意の機能に安全にアクセスします。

---

## 2. インターフェース構造とクラス設計

### 2.1 `SupportAICapability`（インターフェース）
サポートAIが利用可能な機能を定義する共通インターフェースです。
- `String getId()`: 能力を一意に識別するID（例: `"security-check"`, `"list-workflows"`, `"file-lookup"`）。
- `String execute(String argument)`: パラメータを受け取り、実行結果を文字列形式で返します。

### 2.2 `SupportAiService`（レジストリ・管理者）
登録された能力の管理と呼び出しの仲介を行います。
- `registerCapability(SupportAICapability)`
- `unregisterCapability(String)`
- `String invoke(String capabilityId, String argument)`: 指定された能力を安全に実行し、存在しない場合はエラーを返します。

---

## 3. 標準搭載のCapability（13カテゴリ）

初期状態で以下の13個の汎用Capabilityが事前登録されます。

### 3.1 セキュリティチェック (`security-check`)
セキュリティルールに基づき、特定のアクションが許可されているかを検証します。
- **引数形式**: `"<category>:<action>"` （例：`"file-access:/workspace/docs/secrets/credentials.txt"`）
- **戻り値**: `"PERMITTED"`（許可） または `"BLOCKED"`（拒否）

### 3.2 ワークフロー一覧取得 (`list-workflows`)
システムに組み込まれた定義済みワークフローおよびユーザー独自のカスタムワークフローを一覧として取得し、フォーマットされた解説を返します。
- **引数**: 不要（`null` または空文字）
- **戻り値**: ワークフロー詳細一覧テキスト

### 3.3 ファイル調査 (`file-lookup`)
ワークスペース内に特定のファイルやフォルダが存在するかを安全に特定します。
- **引数**: 相対または絶対ファイルパス
- **戻り値**: `"EXISTS"`（ファイル存在）、`"DIRECTORY"`（ディレクトリ存在）、`"NOT_FOUND"`（未検出）

### 3.4 Gitステータス取得 (`git-status`)
ワークスペース内のGitリポジトリの現在のステータス（ブランチ名、変更ファイル、未追跡ファイル等）を取得します。
- **引数**: オプション（ワークスペース相対サブディレクトリパス。未指定時はワークスペースルート）
- **戻り値**: Gitステータス詳細テキスト

### 3.5 Gitログ取得 (`git-log`)
ワークスペース内のGitリポジトリの直近コミット履歴を取得します。
- **引数**: オプション（取得コミット件数。デフォルト5件）
- **戻り値**: 直近コミット履歴テキスト

### 3.6 ファイル内容読込 (`file-read`)
セキュリティルールを尊重しながら、ワークスペース内のテキストファイルの指定行数を読み込みます。
- **引数形式**: `"相対ファイルパス"` または `"相対ファイルパス:最大行数"`
- **戻り値**: ファイルの内容テキスト（セキュリティブロック時は `"BLOCKED_BY_SECURITY"`）

### 3.7 ディレクトリ一覧取得 (`directory-list`)
指定されたディレクトリ内のファイルおよびサブディレクトリの一覧を取得します。
- **引数**: オプション（相対ディレクトリパス。未指定時はワークスペースルート）
- **戻り値**: ディレクトリ配下のファイル一覧テキスト

### 3.8 ワークスペース情報取得 (`workspace-info`)
アクティブなワークスペースのパス、Gitリポジトリ有効性、セキュリティルールの有効状態などのメタ情報を取得します。
- **引数**: 不要（`null` または空文字）
- **戻り値**: ワークスペース概要テキスト

### 3.9 Ollama接続ステータス取得 (`ollama-status`)
Ollamaサーバーへの接続状態および利用可能なモデル一覧を確認します。
- **引数**: オプション（OllamaベースURL。デフォルト `http://localhost:11434`）
- **戻り値**: 接続状態および利用可能モデル一覧テキスト

### 3.10 セキュリティルール一覧取得 (`security-rules-list`)
現在設定されているセキュリティルール一覧およびルールの有効/無効状態を取得します。
- **引数**: 不要（`null` または空文字）
- **戻り値**: 設定済みセキュリティルール一覧テキスト

### 3.11 ファイル検索 (`file-search`)
ワークスペース内で指定されたキーワードまたはパターンを含むファイル名を検索します。
- **引数**: 検索キーワード文字列
- **戻り値**: マッチしたワークスペース相対パスの一覧テキスト

### 3.12 システム環境情報取得 (`system-info`)
OS名、Javaバージョン、プロセッサ数、メモリ使用状況などの動作環境情報を取得します。
- **引数**: 不要（`null` または空文字）
- **戻り値**: システム動作環境テキスト

### 3.13 現在日時取得 (`dateTime-now`)
現在の日付および時刻を取得します。
- **引数**: オプション（DateTimeFormatterフォーマットパターン。デフォルト `"yyyy-MM-dd HH:mm:ss"`）
- **戻り値**: フォーマットされた現在日時テキスト

---

## 4. 拡張ユースケース例

### シナリオ1: Webアクセス検査機能の追加
サポートAIにWeb接続確認ツール（例: 特定のAPIが稼働中か調べる）を持たせる場合、以下のように新しいCapabilityを実装して登録するだけで完了します。

```java
public class WebPingCapability implements SupportAICapability {
    @Override
    public String getId() {
        return "web-ping";
    }

    @Override
    public String execute(String url) {
        // HTTP接続テスト
        boolean ok = performHttpPing(url);
        return ok ? "ONLINE" : "OFFLINE";
    }
}

// 登録
SupportAiService.getInstance().registerCapability(new WebPingCapability());

// 呼び出し（口）
String status = SupportAiService.getInstance().invoke("web-ping", "https://api.github.com");
```
これにより、サポートAIの評価ロジックや本体コードを汚染することなく、自律的に利用できる口を極めてシンプルに拡張できます。
