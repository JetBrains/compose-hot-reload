/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.hotReloadUI

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec.milliseconds
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec.statusColorFadeDuration
import org.jetbrains.compose.devtools.api.ReloadColors
import org.jetbrains.compose.devtools.api.ReloadEffect
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.hotReloadUI.widgets.Divider
import org.jetbrains.compose.hotReloadUI.widgets.TimeAgoText
import org.jetbrains.compose.hot_reload.hot_reload_runtime_jvm.generated.resources.Res
import org.jetbrains.compose.hot_reload.hot_reload_runtime_jvm.generated.resources.error
import org.jetbrains.compose.resources.painterResource

internal class ErrorNotificationOverlayEffect : ReloadEffect.OverlayEffect {
    @Composable
    override fun effectOverlay(state: ReloadState) {

        var failure by remember { mutableStateOf<ReloadState.Failed?>(null) }
        if (state is ReloadState.Failed) {
            failure = state
        }

        var visible by remember(state) { mutableStateOf(state is ReloadState.Failed) }

        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, easing = LinearEasing)),
                exit = fadeOut(tween(1000, easing = LinearEasing)),
            ) {

            }


            AnimatedVisibility(
                visible = visible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                Box(
                    modifier = Modifier
                        .animateEnterExit(
                            enter = fadeIn(tween(statusColorFadeDuration.milliseconds, easing = LinearEasing)),
                            exit = fadeOut(tween(statusColorFadeDuration.milliseconds, easing = LinearEasing)),
                        )
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    surface.copy(alpha = 0.3f),
                                    surface.copy(alpha = 0f)
                                )
                            )
                        )
                        .fillMaxSize()
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                        .animateEnterExit(
                            enter = slideInVertically(initialOffsetY = { -it }),
                            exit = slideOutVertically(targetOffsetY = { -it })
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ErrorNotification(
                        failure ?: return@Box,
                        onClick = { visible = false }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ErrorNotification(
    failed: ReloadState.Failed,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = 12.dp, shape = RoundedCornerShape(12.dp),
                ambientColor = ReloadColors.error,
                spotColor = ReloadColors.error
            )
            .clickable { onClick() }
            .background(surface)

            .border(color = ReloadColors.error, width = .75.dp, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)

    ) {

        val errorSvg = painterResource(Res.drawable.error)
        Column(
            modifier = Modifier.wrapContentWidth(Alignment.Start)
                .width(IntrinsicSize.Max),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    errorSvg,
                    "Error",
                    modifier = Modifier.size(12.dp).align(Alignment.CenterVertically)
                )
                Spacer(Modifier.width(4.dp))
                BasicText(
                    "Hot Reload Failed",
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle.Default.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = ColorProducer { Color.White }
                )
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                failed.reason,
                style = TextStyle.Default.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                overflow = TextOverflow.Ellipsis,
                color = ColorProducer { Color.White })


            Divider(color = Color.White, modifier = Modifier.padding(vertical = 8.dp), thickness = .5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeAgoText(
                    style = TextStyle.Default.copy(fontSize = 10.sp),
                    color = Color.White,
                )

                // Minimum space between the texts
                Spacer(Modifier.width(8.dp))

                BasicText(
                    "Click to dismiss...",
                    style = TextStyle.Default.copy(fontSize = 10.sp),
                    color = ColorProducer { Color.White },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val surface = Color(0xFF3C3F41)
