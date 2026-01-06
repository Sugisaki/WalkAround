package com.studiokei.walkaround.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * アプリ内全体で使用する日時フォーマットのユーティリティ。
 */
object DateTimeFormatUtils {
    /**
     * セクションヘッダー用の日付フォーマッタ。
     * 表示例：「2026年1月6日（火）」
     */
    val headerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)")
        .withZone(ZoneId.systemDefault())
        .withLocale(Locale.JAPANESE)

    /**
     * 詳細な日時表示用のフォーマッタ。
     * 表示例：「2026/01/06 12:34:56」
     */
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
}
