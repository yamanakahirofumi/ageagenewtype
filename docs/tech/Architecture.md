# アーキテクチャ設計

本アプリケーションでは、LangChain4j ライブラリを経由してローカル環境で動作する Ollama を生成AIエンジンとして活用し、IDE風の統合開発・対話インターフェース（ファイルツリー、エディタ、チャット部）を提供します。柔軟な機能拡張に対応するため、標準的な MVC (Model-View-Controller) パターンを採用し、各層の責務を明確に分離します。

## 1. ディレクトリ・パッケージ構造
標準的な Maven 構造および JavaFX のモジュール・システム（JPMS）に準拠した構成を採用します。

```
.
├── pom.xml                # プロジェクト構成 (Maven)
├── src
│   ├── main
│   │   ├── java
│   │   │   ├── module-info.java  # モジュール定義
│   │   │   └── net.hero.genai
│   │   │       ├── Main.java     # エントリーポイント
│   │   │       ├── model/        # ドメインモデル・ビジネスロジック
│   │   │       │   ├── prompt/   # プロンプト、対話履歴、テンプレートの定義
│   │   │       │   └── generation/ # テキスト生成、推論・処理ロジック
│   │   │       ├── view/         # JavaFX FXML および カスタムコントロール
│   │   │       ├── controller/   # UI 制御（FXML Controller）
│   │   │       ├── service/      # 外部API通信、DB・ファイル入出力
│   │   │       └── util/         # 共通ユーティリティ（日付、テキスト処理）
│   │   └── resources
│   │       └── net.hero.genai
│   │           ├── fxml/         # UI レイアウト
│   │           └── css/          # スタイルシート
│   └── test
│       └── java
│           └── net.hero.genai    # ユニットテスト
```

## 2. 主要コンポーネントの責務

### 2.1 Model 層
- **WorkspaceFile**: ワークスペース内のファイルおよびディレクトリ構造を保持・管理するドメインモデル。
- **ChatSession**: ユーザーの対話セッション全体の集約ルート。対話履歴、使用中の Ollama モデル、プロンプトコンテキストを管理します。
- **Prompt**: プロンプトテンプレートや指示文情報を表す record / class。ファイルコンテキストの変数挿入に対応 (`PromptTemplate` の活用)。
- **Message**: ユーザーの入力文、Ollama からの AI 応答メッセージ、モデル名や評価パラメータを表す record。
- **GenerationEngine**: LangChain4j の抽象化モデル (`ChatLanguageModel`, `StreamingChatLanguageModel`) を利用し、コンテキスト構築、プロンプト条件適用、AIテキスト生成・ストリーミング処理をカプセル化したコンポーネント。

### 2.2 View 層
IDE 風の3分割レイアウトを基本構造として構成します。
- **FileTreeView (ファイルツリー部)**: ワークスペース内のファイル・フォルダ構造を表示するツリービューコンポーネント (`FileTree.fxml`)。
- **EditorView (エディタ部)**: 選択されたファイルの内容やプロンプトテンプレートを閲覧・編集するテキストエディタコンポーネント (`Editor.fxml`)。
- **ChatView (チャット部)**: Ollama との対話履歴、入力プロンプト、モデル選択、応答ストリーミング表示を行うチャット UI コンポーネント (`ChatView.fxml`)。
- **MainWorkspaceView**: 上記3つのビューを `SplitPane` により IDE 風レイアウトに統合するメイン画面 (`MainWorkspace.fxml`)。

### 2.3 Controller 層
- **MainWorkspaceController**: 全体レイアウト（SplitPane）の制御および各領域（ファイルツリー、エディタ、チャット部）間のイベント調整を担当します。
- **FileTreeController**: ファイルツリーの操作（ファイル選択、追加、削除など）イベントを処理します。
- **EditorController**: エディタのファイル読み込み、変更検出、保存イベントを処理します。
- **ChatController**: チャット入力、モデル切り替え、LangChain4j を通じた非同期通信による応答描画を管理します。

### 2.4 Service 層
- **OllamaApiService**: LangChain4j (`langchain4j-ollama`) を経由して Ollama サービスと連携し、モデル取得 (`OllamaModels`)、プロンプト送信、応答ストリーミング (`StreamingChatLanguageModel` / `TokenStream`) を制御するサービス。
- **WorkspaceFileService**: ワークスペース内のファイル読み書き、ディレクトリツリー構築を担当するサービス。
- **PersistenceService**: SQLite への対話履歴・設定情報の永続化を担当します。詳細は [データベース選定方針](./Database-Selection.md) を参照。

## 3. 設計方針
- **データの不変性 (Immutability)**: 対話履歴や生成結果などの過去データについては Java の `record` を活用し、不変性を担保することでデバッグの容易性を高めます。
- **リアクティブなUI更新**: JavaFX の `Property` や `ObservableList` を活用し、Model の変更が自動的に View に反映されるように設計します。
- **例外処理の統一化**: 外部API通信やファイルIOなど、失敗の可能性がある処理については、[例外処理方針](../tech/Error-Handling-Policy.md) に基づき一貫したエラーハンドリングを行います。
