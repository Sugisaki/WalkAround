# WalkAround プロジェクトドキュメント

このディレクトリには、WalkAround アプリの開発・メンテナンスのための仕様書および設計ドキュメントが格納されています。

## 目次

### 1. [アーキテクチャ設計 (Architecture)](architecture/overview.md)
アプリ全体の構造、データフロー、および主要なコンポーネントの設計について解説します。

### 2. [機能仕様 (Features)](features/tracking.md)
- [バックグラウンド計測とロジック](features/tracking.md): 位置情報・歩数計測、住所案内、フィルタリングロジック。
- [画面遷移とUI構成](features/ui_flow.md): NavigationSuite を使用した画面構成と遷移フロー。

### 3. [外部連携・API (API)](api/external_services.md)
Google Maps SDK、Play Services Location、Health Connect、および必要な権限についての仕様。

---

## 開発環境のセットアップ

1.  **Android Studio**: 最新の安定版を推奨。
2.  **API Key**: `local.properties` に `GOOGLE_MAPS_API_KEY` を設定してください。
3.  **権限**: 計測機能のテストには、位置情報および身体活動の権限許可が必要です。
