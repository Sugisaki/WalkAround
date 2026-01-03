package com.studiokei.walkaround.util

object Constants {
    /**
     * 住所の取得・比較を行う最小の間隔（ミリ秒）
     * 頻繁なジオコーディング API の呼び出しを抑制するために使用します。
     */
    const val ADDRESS_PROCESS_INTERVAL_MS = 60 * 1000L
}
