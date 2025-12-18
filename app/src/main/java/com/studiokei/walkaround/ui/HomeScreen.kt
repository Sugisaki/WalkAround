package com.studiokei.walkaround.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(AppDatabase.getDatabase(context))
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "現在の歩数",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${uiState.currentStepCount}",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isRunning) {
                // Stop and +1 Step buttons
                Button(onClick = { homeViewModel.stopTracking() }) {
                    Text("ストップ")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { homeViewModel.incrementStepCount() }) {
                    Text("歩数アップ")
                }
            } else {
                // Start button
                Button(onClick = { homeViewModel.startTracking() }) {
                    Text("スタート")
                }
            }
        }
    }
}
