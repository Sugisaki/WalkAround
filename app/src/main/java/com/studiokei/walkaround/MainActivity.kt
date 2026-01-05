package com.studiokei.walkaround

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.studiokei.walkaround.ui.HomeScreen
import com.studiokei.walkaround.ui.MapScreen
import com.studiokei.walkaround.ui.RouteScreen
import com.studiokei.walkaround.ui.SettingsScreen
import com.studiokei.walkaround.ui.theme.WalkaroundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalkaroundTheme {
                WalkaroundApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun WalkaroundApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    // 選択されたセクションIDを保持（スクロール制御に使用）
    var selectedSectionId by rememberSaveable { mutableStateOf<Long?>(null) }

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
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                    onSectionClick = { sectionId ->
                        // Home画面でセクションがタップされたら、Route画面へ遷移しスクロール対象として保持
                        selectedSectionId = sectionId
                        currentDestination = AppDestinations.ROUTE
                    }
                )
                AppDestinations.ROUTE -> RouteScreen(
                    modifier = Modifier.padding(innerPadding),
                    scrollToSectionId = selectedSectionId,
                    onScrollFinished = {
                        // スクロールが完了したらIDをクリアして、意図しない再スクロールを防ぐ
                        selectedSectionId = null
                    },
                    onSectionClick = { sectionId ->
                        // Route画面内でセクションがタップされたら、従来通りMap画面へ遷移
                        selectedSectionId = sectionId
                        currentDestination = AppDestinations.MAP
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
