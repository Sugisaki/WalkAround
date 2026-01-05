package com.studiokei.walkaround.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.studiokei.walkaround.data.database.AppDatabase

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    sectionId: Long? = null // sectionIdを追加
) {
    val mapViewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val database = AppDatabase.getDatabase(application)
                val locationManager = LocationManager(application)
                // SectionService から SectionProcessor への名称変更に対応
                val sectionProcessor = SectionProcessor(database, locationManager)
                MapViewModel(
                    database,
                    locationManager,
                    sectionProcessor
                )
            }
        }
    )

    val track by mapViewModel.track.collectAsStateWithLifecycle()
    val initialBounds by mapViewModel.initialBounds.collectAsStateWithLifecycle()
    val isLoading by mapViewModel.isLoading.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState()

    // 画面表示時、またはsectionIdが変更された時にデータをロード
    LaunchedEffect(sectionId) {
        mapViewModel.loadSection(sectionId)
    }

    LaunchedEffect(initialBounds) {
        initialBounds?.let {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(it, 100))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            if (track.isNotEmpty()) {
                Polyline(
                    points = track,
                    color = Color(0xFF006400), // DarkGreen (濃緑色)
                    width = 10f
                )

                // スタート地点に青い丸のマーク
                Marker(
                    state = MarkerState(position = track.first()),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    title = "スタート"
                )

                // 最後の地点に赤い丸のマーク
                Marker(
                    state = MarkerState(position = track.last()),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    title = "ゴール"
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
