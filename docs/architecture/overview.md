# アーキテクチャ概要

WalkAround は、モダンな Android 開発プラクティスに基づき、関心の分離とリアクティブなデータフローを重視した設計になっています。

## 全体構造

本アプリは **MVVM (Model-View-ViewModel)** パターンに **Repository** 層を組み合わせた構成を採用しています。

-   **UI (View)**: Jetpack Compose を使用した宣言的 UI。
-   **ViewModel**: 画面ごとの状態（State）を管理し、Repository からデータを取得して UI に提供します。
-   **Repository (Data)**: Room データベースを介したローカルデータの永続化を担います。
-   **Service**: `TrackingService` がバックグラウンドでの計測処理を一手に引き受けます。

## 主要コンポーネント

### 1. TrackingService (フォアグラウンドサービス)
アプリの核となるコンポーネントです。UI がバックグラウンドに回っても計測を継続するため、フォアグラウンドサービスとして動作します。
-   位置情報の更新（Fused Location Provider）の受信。
-   歩数センサー（Step Counter）の監視。
-   一定条件（住所の変化など）での TTS (Text-to-Speech) 案内。

### 2. LocationManager
位置情報に関連するロジックをカプセル化しています。
-   `FusedLocationProviderClient` を用いた位置情報のリクエスト。
-   `Geocoder` による逆ジオコーディング（座標から住所への変換）。
-   住所のキャッシュ管理（国コードに基づいたロケールの最適化）。

### 3. AppDatabase (Room)
すべての計測データは Room を使用して SQLite データベースに保存されます。
-   `Section`: 計測セッション。
-   `TrackPoint`: GPS座標の履歴。
-   `AddressRecord`: 通過した住所の記録。
-   `StepSegment`: 各セクションの合計歩数。

## データフロー

1.  **計測時**: `TrackingService` が各マネージャーからデータを取得し、`AppDatabase` に直接保存します。
2.  **表示時**: 各 `ViewModel` は `AppDatabase` から `Flow` としてデータを受け取ります。
3.  **UI更新**: `Flow` の値が更新されると、Compose の `collectAsStateWithLifecycle` を通じて UI が自動的に再描画されます。

このリアクティブな構造により、サービスで保存されたデータがリアルタイムに画面（Home や Route）に反映されます。
