package com.studiokei.walkaround

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.ui.HomeScreen
import com.studiokei.walkaround.ui.MapScreen
import com.studiokei.walkaround.ui.RouteScreen
import com.studiokei.walkaround.ui.SettingsScreen
import com.studiokei.walkaround.ui.SettingsViewModel
import com.studiokei.walkaround.ui.theme.WalkaroundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // アプリ全体の設定を管理するViewModelを取得
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(AppDatabase.getDatabase(applicationContext))
                    }
                }
            )
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            // テーマ（ライト/ダーク）の判定ロジック
            val darkTheme = if (settingsUiState.followSystemTheme) {
                isSystemInDarkTheme()
            } else {
                settingsUiState.isDarkMode
            }

            WalkaroundTheme(darkTheme = darkTheme) {
                WalkaroundApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun WalkaroundApp() {
    // ナビゲーションの履歴を管理するバックスタック
    var backStack by rememberSaveable { mutableStateOf(listOf(AppDestinations.HOME)) }
    // 選択されたセクションIDを保持（スクロールおよびハイライト制御に使用）
    var selectedSectionId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 現在表示している画面
    val currentDestination = backStack.last()

    // 戻るボタンが押されたときの処理を定義
    BackHandler(enabled = true) {
        // バックスタックに前の画面があれば、1つ前に戻る
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
        // バックスタックが1つの場合は何もしない (アプリを終了しない)
    }

    // 画面階層を深くするナビゲーション（例：HOME -> ROUTE）
    fun navigateTo(destination: AppDestinations) {
        backStack = backStack + destination
    }

    // トップレベルの画面へのナビゲーション（ボトムメニューからの遷移など）
    fun navigateToTopLevel(destination: AppDestinations) {
        // バックスタックを新しい画面でリセット
        backStack = listOf(destination)
        // HOME画面に戻った場合は、選択中のセクションIDを解除
        if (destination == AppDestinations.HOME) {
            selectedSectionId = null
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { 
                        // トップレベルの画面として遷移
                        navigateToTopLevel(it)
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                    onSectionClick = { sectionId ->
                        // Home画面でセクションがタップされたら、Route画面へ遷移しIDを保持
                        selectedSectionId = sectionId
                        // 画面をスタックに追加
                        navigateTo(AppDestinations.ROUTE)
                    }
                )
                AppDestinations.ROUTE -> RouteScreen(
                    modifier = Modifier.padding(innerPadding),
                    scrollToSectionId = selectedSectionId,
                    onScrollFinished = {
                        // RouteScreen内部でスクロール済みフラグを管理するようになったため、
                        // ここではIDをクリアせず、ハイライト表示のために保持し続ける。
                    },
                    onSectionClick = { sectionId ->
                        // Route画面内でセクションがタップされたら、Map画面へ遷移
                        selectedSectionId = sectionId
                        // 画面をスタックに追加
                        navigateTo(AppDestinations.MAP)
                    }
                )
                AppDestinations.MAP -> MapScreen(
                    modifier = Modifier.padding(innerPadding),
                    sectionId = selectedSectionId
                )
                AppDestinations.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    ROUTE("Route", Icons.Default.List),
    MAP("Map", Icons.Default.Place),
    SETTINGS("Settings", Icons.Default.Settings),
}
