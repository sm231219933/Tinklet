package com.tinklet.bharatdatingapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableCard(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val rotation = (offsetX.value / 60).coerceIn(-15f, 15f)
    val alpha = 1f - (abs(offsetX.value) / 1000f).coerceIn(0f, 0.5f)

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val targetX = if (offsetX.value > 400f) 1000f else if (offsetX.value < -400f) -1000f else 0f
                        scope.launch {
                            if (targetX != 0f) {
                                offsetX.animateTo(targetX, tween(300))
                                if (targetX > 0) onSwipeRight() else onSwipeLeft()
                                // Reset for next card if needed, though usually this card is removed from stack
                            } else {
                                launch { offsetX.animateTo(0f, spring()) }
                                launch { offsetY.animateTo(0f, spring()) }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                )
            }
    ) {
        content()
    }
}
