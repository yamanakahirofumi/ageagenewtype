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

## 3. 標準搭載のCapability（3カテゴリ）

初期状態で以下の3つの汎用Capabilityが事前登録されます。

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
