# ドキュメント一覧

このディレクトリには、LangChain4j および Ollama を活用した IDE 風生成AIデスクトップアプリに関する詳細なドキュメントが格納されています。

## 1. フォルダ構成と配置

ドキュメントは内容に応じて以下のいずれかに分類して配置します。

- **`docs/features/`**：機能仕様、ビジネスルール、UI/UXデザインなど、ユーザーの要求に近い内容。
- **`docs/tech/`**：技術スタック、アーキテクチャ、コーディング規約、CI/CDなど、一般的な技術・開発設定に関する内容。
- **`docs/implementation/`**：特定機能の実装方法、データ構造、最適化手法など、詳細な実装に関する内容。

---

## 2. 機能・仕様 (`docs/features/`)
- [仕様書](features/Specifications.md)：プロジェクト概要、主要機能、UI/UX の概要
- [エージェント・セキュリティマネージャ仕様書](features/Agent-Security-Manager.md)：AIエージェントの安全な実行制限に関する仕様（ファイル、外部実行、HTTP）
- [エージェント・セキュリティマネージャGUI仕様書](features/Agent-Security-Manager-GUI-Specification.md)：セキュリティ制限、バイパス設定、警告表示、監査ログ表示などのGUI設計仕様
- [Git操作GUI仕様書](features/Git-Control-Specification.md)：GUIからGit操作（init, fetch, pull, commit, push, checkout, branch作成）を行うための機能仕様
- [機能一覧](features/Functional-List.md)：本アプリケーションで提供するカテゴリ別の機能詳細
- [画面一覧と遷移仕様](features/Screen-Transitions.md)：各画面の役割、主要項目、および画面間の遷移
- [画面詳細設計](features/Screen-Details.md)：各画面のレイアウト、コンポーネント、表示項目の詳細
- [論理エンティティ](features/Logical-Entities.md)：ドメインモデルの定義と関係性
- [データベーススキーマ](features/Database-Schema.md)：テーブル構造、制約、および ER 図
- [動作環境](features/System-Requirements.md)：アプリケーションを実行するために必要な最低・推奨スペック

## 3. 一般的な技術・開発設定 (`docs/tech/`)
- [アーキテクチャ設計](tech/Architecture.md)：システムのパッケージ構造と主要クラスの責務
- [データベース選定方針](tech/Database-Selection.md)：使用するデータベースの選定理由と特徴
- [エラーハンドリング方針](tech/Error-Handling-Policy.md)：基本方針と各ケースでの対応
- [ロギング方針](tech/Logging-Policy.md)：デバッグおよび保守のためのログ出力指針
- [技術スタック](tech/Tech-Stack.md)：使用している言語、ライブラリ、ツールなどの情報
- [CI 設定](tech/CI-Setting.md)：GitHub Actions を利用した自動ビルドとテストの設定について
- [テストルール](tech/Test-Rule.md)：テストケース作成の一般的なガイドライン
- [品質方針](tech/Quality-Policy.md)：フェーズ（仕様未確定/確定）に応じた品質の考え方と到達目標
- [配布方法](tech/Distribution-Method.md)：カスタム JRE による配布パッケージの作成について
- [コーディング規約](tech/Coding-Convention.md)：クラス作成基準（record, final の使用等）について
- [仕様書の書き方ルール](tech/Specification-Rule.md)：本プロジェクトにおけるドキュメント作成基準
- [TODOリストの書き方ルール](tech/TODO-Rule.md)：検討事項の追加・更新ルール

## 4. 特定機能の実装方法 (`docs/implementation/`)
- [JUnit 5 ルール](implementation/JUnit-Rule.md)：JUnit 5 を使用したテストの実装方法

## 5. 検討事項（TODOリスト）
開発を進めるにあたって検討・具体化が必要な事項のリストです。
追加・変更を含む詳細な内容は [検討事項・TODOリスト](TODO-Details.md) を参照してください。
以降の検討事項の更新は、詳細ファイルのみで行います。
