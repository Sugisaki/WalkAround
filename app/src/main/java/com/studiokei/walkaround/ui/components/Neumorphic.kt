package com.studiokei.walkaround.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.studiokei.walkaround.ui.theme.LocalIsDarkTheme

// --- カラー定義 ---

// ライトテーマ用
val NeumorphicLightBg = Color(0xFFEDF1F4)
val NeumorphicLightLightShadow = Color.White
val NeumorphicLightDarkShadow = Color(0xFFC0C8D0)

// ダークテーマ用
val NeumorphicDarkBg = Color(0xFF1B1B1F)
val NeumorphicDarkLightShadow = Color(0xFF404050)
val NeumorphicDarkDarkShadow = Color(0xFF0E0E11)

/**
 * 現在のテーマに基づいた背景色
 */
@Composable
fun getNeumorphicBg(): Color = if (LocalIsDarkTheme.current) NeumorphicDarkBg else NeumorphicLightBg

/**
 * 現在のテーマに基づいたシャドウの色（明/暗）
 */
@Composable
fun getNeumorphicShadows(): Pair<Color, Color> {
    return if (LocalIsDarkTheme.current) {
        NeumorphicDarkLightShadow to NeumorphicDarkDarkShadow
    } else {
        NeumorphicLightLightShadow to NeumorphicLightDarkShadow
    }
}

/**
 * ニューモーフィズムの基本サーフェス
 */
@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val bgColor = getNeumorphicBg()
    val (lightShadow, darkShadow) = getNeumorphicShadows()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Light Shadow (Top-Left)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = -elevation, y = -elevation)
                .blur(elevation)
                .background(lightShadow, shape)
        )
        // Dark Shadow (Bottom-Right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = elevation, y = elevation)
                .blur(elevation)
                .background(darkShadow, shape)
        )
        // Main Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor, shape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * ニューモーフィズムスタイルのボタン
 */
@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(if (isPressed) 2.dp else 6.dp, label = "elevation")
    
    val bgColor = getNeumorphicBg()
    val (lightShadow, darkShadow) = getNeumorphicShadows()

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!isPressed) {
            // Shadows for popped out effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = -elevation, y = -elevation)
                    .blur(elevation)
                    .background(lightShadow, shape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = elevation, y = elevation)
                    .blur(elevation)
                    .background(darkShadow, shape)
            )
        }
        
        // Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor, shape)
                .then(
                    if (isPressed) Modifier.border(1.dp, darkShadow.copy(alpha = 0.5f), shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
