# 外部連携・API 仕様

WalkAround が利用している外部 SDK、Google Play Services、および Android システム権限に関する仕様について説明します。

## 1. Google Maps SDK (Maps Compose)

地図表示と経路（ポリライン）の描画に使用しています。

-   **ライブラリ**: `com.google.maps.android:maps-compose`
-   **API Key**: Google Cloud Console で作成した API キーを `AndroidManifest.xml` の `com.google.android.geo.API_KEY` メタデータとして設定します。
-   **主な用途**:
    -   `GoogleMap` コンポーザブルによる地図表示。
    -   `Polyline` による歩行軌跡の描画。
    -   `Marker` による現在地または特定の住所記録地点の表示。

## 2. Location Services (Fused Location Provider)

高精度かつ低消費電力な位置情報取得のために使用しています。

-   **ライブラリ**: `com.google.android.gms:play-services-location`
-   **ロジック**: `LocationManager` クラスでカプセル化されています。
-   **逆ジオコーディング**: `android.location.Geocoder` を使用。
    -   Android 13 (Tiramisu / API 33) 以降では、UI スレッドをブロックしない非同期リスナー API (`getFromLocation` のコールバック形式) を使用しています。
    -   それ以前のバージョンでは同期 API をバックグラウンドスレッドで実行しています。

## 3. Health Connect (ヘルスコネクト)

歩数データの読み取り、および将来的な他フィットネスアプリとの連携のために導入されています。

-   **ライブラリ**: `androidx.health.connect:connect-client`
-   **機能**:
    -   歩数データ (`StepsRecord`) の読み取り。
    -   `HealthConnectManager` を通じて権限の確認と要求を行います。
-   **設定**: `AndroidManifest.xml` に `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` の Intent Filter を持つ Activity（本アプリでは MainActivity）が必要です。

## 4. Android システム権限 (Permissions)

アプリが正常に動作するために、以下の権限を `AndroidManifest.xml` で宣言しています。

| 権限 | 用途 |
| :--- | :--- |
| `ACCESS_FINE_LOCATION` | GPS による高精度な位置情報の取得。 |
| `ACCESS_COARSE_LOCATION` | ネットワークベースのおおまかな位置情報の取得。 |
| `ACTIVITY_RECOGNITION` | デバイスの歩数計センサーへのアクセス。 |
| `READ_STEPS` | ヘルスコネクトからの歩数データの読み取り（カスタム権限）。 |
| `FOREGROUND_SERVICE` | バックグラウンド計測を維持するためのサービス実行権限。 |
| `FOREGROUND_SERVICE_LOCATION` | Android 14 以降で位置情報サービスを実行するために必須。 |
| `FOREGROUND_SERVICE_HEALTH` | Android 14 以降で身体活動関連サービスを実行するために必須。 |
| `POST_NOTIFICATIONS` | Android 13 以降で、計測中の通知を表示するために必要。 |

## 5. デバイス内センサー

-   **センサータイプ**: `Sensor.TYPE_STEP_COUNTER`
-   **動作**: `StepSensorManager` がハードウェアセンサーを監視し、`TrackingService` に歩数変化を通知します。Health Connect が利用可能な場合は、そちらのデータとの整合性を考慮する設計となっています。
