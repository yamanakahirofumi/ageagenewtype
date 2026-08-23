# アーキテクチャ設計

本アプリケーションでは、LangChain4j ライブラリを経由してローカル環境で動作する Ollama を生成AIエンジンとして活用し、IDE風の統合開発・対話インターフェース（ファイルツリー、エディタ、チャット部）を提供します。保守性と拡張性を高めるため、コントローラやサービスなどのレイヤー別ではなく、**機能（ドメイン・フィーチャー）単位**で Java パッケージ（Namespace）を分割する構造を採用します。

## 1. ディレクトリ・パッケージ構造
標準的な Maven 構造および JavaFX のモジュール・システム（JPMS）に準拠した機能別パッケージ構成を採用します。

```
.
├── pom.xml                # プロジェクト構成 (Maven)
├── src
│   ├── main
│   │   ├── java
│   │   │   ├── module-info.java  # モジュール定義
│   │   │   └── net.hero.genai
│   │   │       ├── Main.java     # エントリーポイント
│   │   │       ├── chat/         # チャット機能 (ChatController, ChatSession, Message)
│   │   │       ├── git/          # Git連携機能 (GitController, GitService, GitStatus)
│   │   │       ├── ollama/       # Ollama通信機能 (OllamaConfigController, OllamaApiService, OllamaConfig, ChatStreamListener)
│   │   │       ├── security/     # セキュリティマネージャ機能 (SecuritySettingsController, SecurityService, SecurityRule, AuditLogEntry)
│   │   │       ├── supportai/    # サポートAI Capability機能 (SupportAiService, SupportAICapability, 各Capability実装)
│   │   │       ├── workflow/     # AIワークフローエンジン機能 (WorkflowService, Workflow, WorkflowStep, WorkflowStepStatus)
│   │   │       └── workspace/    # ワークスペース・エディタ機能 (MainWorkspaceController, FileTreeController, EditorController, WorkspaceFile, WorkspaceFileService, WorkspaceFileTools, WorkspaceAgent)
│   │   └── resources
│   │       └── net.hero.genai
│   │           ├── fxml/         # UI レイアウト
│   │           └── css/          # スタイルシート
│   └── test
│       └── java
│           └── net.hero.genai    # 機能別に分割されたユニットテスト
```

## 2. 主要機能パッケージの責務

### 2.1 chat (チャット機能)
- **ChatController**: チャット画面の UI 制御、プロンプト送信、モデル・ワークフロー選択およびストリーミング応答描画。
- **ChatSession**: 対話セッション情報および対話履歴メッセージの集約管理。
- **Message**: ユーザー入力および AI 応答メッセージを表す不変ドメインモデル (`record`)。

### 2.2 git (Git 連携機能)
- **GitController**: Git パネル UI 制御、ブランチ切り替え、ステージング選択、コミット/プッシュ操作処理。
- **GitService**: JGit を用いたリポジトリ操作、ステータス取得、ブランチ管理・リモート連携。
- **GitStatus**: リポジトリの各種変更状態を表すドメインモデル (`record`)。

### 2.3 ollama (Ollama 通信機能)
- **OllamaConfigController**: Ollama 接続設定 UI の制御および接続テストの実行。
- **OllamaApiService**: LangChain4j (`langchain4j-ollama`) 経由での Ollama 連携、モデル取得、テキスト生成およびストリーミング制御。
- **OllamaConfig**: Ollama 接続設定情報を保持するデータモデル。
- **ChatStreamListener**: ストリーミング応答受食用リスナーインターフェース。

### 2.4 security (セキュリティマネージャ機能)
- **SecuritySettingsController**: セキュリティ設定・ルール編集および監査ログ表示 UI 制御。
- **SecurityService**: セキュリティルール (`security_rules.conf`) の読み込み・検証・保存、パーミッション判定および監査ログ記録。
- **SecurityRule**: セキュリティルール定義モデル (`record`)。
- **AuditLogEntry**: セキュリティ判定の監査ログエントリモデル (`record`)。

### 2.5 supportai (サポート AI Capability 機能)
- **SupportAiService**: サポート AI が利用可能な Capability の登録・呼び出しの統合管理サービス。
- **SupportAICapability**: サポート AI 拡張機能の共通インターフェース。
- **Capability 実装群**: `security-check`, `list-workflows`, `file-lookup`, `git-status`, `file-read`, `directory-list` などの個別動的機能プラグイン。

### 2.6 workflow (AI ワークフローエンジン機能)
- **WorkflowService**: 組み込み/カスタムワークフローのロード、自動判定/対話型セッション、多段階タスク実行、自動検証・ループバック制御。
- **Workflow**: ワークフロー定義を表すモデル (`record`)。
- **WorkflowStep**: ワークフローの個別の処理ステップ定義を表すモデル (`record`)。
- **WorkflowStepStatus**: ステップ実行状態の列挙型 (`enum`)。

### 2.7 workspace (ワークスペース・エディタ機能)
- **MainWorkspaceController**: 全体 IDE 風レイアウト (`MainWorkspace.fxml`) の制御および各サブコンポーネントの調整。
- **FileTreeController**: ワークスペースファイルツリー表示およびフォルダ選択操作処理。
- **EditorController**: タブ式テキストエディタのファイル読み込み・編集・保存処理。
- **WorkspaceFile**: ワークスペース内のファイル/フォルダ構造を表すモデル。
- **WorkspaceFileService**: ワークスペースツリー構築、ファイル入出力サービス。
- **WorkspaceFileTools**: LangChain4j Tool Calling 対応のファイルアクセスツール群。
- **WorkspaceAgent**: AI とのやり取りを定義する LangChain4j AI Services インターフェース。

## 3. 設計方針
- **機能別パッケージ分割**: コントローラやサービスなどのレイヤーではなく機能単位で Namespace を切り、凝集度を高めます。
- **データの不変性 (Immutability)**: 対話履歴や設定データなどには Java の `record` を活用し、不変性を高めます。
- **リアクティブなUI更新**: JavaFX の `Property` や `ObservableList` を活用し、Model の変更が自動的に View に反映されるように設計します。
- **例外処理の統一化**: 外部API通信やファイルIOなど、失敗の可能性がある処理については、[例外処理方針](../tech/Error-Handling-Policy.md) に基づき一貫したエラーハンドリングを行います。
