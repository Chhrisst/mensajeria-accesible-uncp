package com.example.mensajeria.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

data class ExploreTarget(
    val id: String,
    val label: String,
    val bounds: Rect,
    val onHover: (() -> Unit)? = null
)

class TouchExploreRegistry {
    private val targets = LinkedHashMap<String, ExploreTarget>()

    fun register(id: String, label: String, bounds: Rect, onHover: (() -> Unit)? = null) {
        if (bounds.width > 0f && bounds.height > 0f) {
            targets[id] = ExploreTarget(id = id, label = label, bounds = bounds, onHover = onHover)
        }
    }

    fun unregister(id: String) {
        targets.remove(id)
    }

    fun clear() {
        targets.clear()
    }

    fun findTargetAt(position: Offset): ExploreTarget? {
        return targets.values
            .filter { it.bounds.contains(position) }
            .minByOrNull { it.bounds.width * it.bounds.height }
    }
}

val LocalTouchExploreRegistry = compositionLocalOf<TouchExploreRegistry?> { null }

/**
 * Registra cualquier botón o elemento interactivo para que al deslizar el dedo
 * sobre la pantalla el narrador anuncie brevemente qué está observando el usuario.
 */
fun Modifier.exploreTarget(
    id: String,
    label: String,
    onHover: (() -> Unit)? = null
): Modifier = composed {
    val registry = LocalTouchExploreRegistry.current
    val currentLabel = rememberUpdatedState(label)
    val currentOnHover = rememberUpdatedState(onHover)

    if (registry == null) {
        this
    } else {
        DisposableEffect(id) {
            onDispose {
                registry.unregister(id)
            }
        }
        this.onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) {
                registry.register(
                    id = id,
                    label = currentLabel.value,
                    bounds = coordinates.boundsInRoot(),
                    onHover = currentOnHover.value
                )
            }
        }
    }
}

/**
 * Contenedor raíz que intercepta el deslizamiento de un dedo para anunciar
 * brevemente cada botón o elemento sin activarlo accidentalmente, permitiendo
 * que un toque directo (tap) active el botón con normalidad.
 */
@Composable
fun AccessibleTouchExplorationHost(
    enabled: Boolean,
    screenKey: Any,
    onExploreHover: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val registry = remember(screenKey) { TouchExploreRegistry() }
    val currentOnExploreHover = rememberUpdatedState(onExploreHover)

    CompositionLocalProvider(LocalTouchExploreRegistry provides registry) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, screenKey) {
                    if (!enabled) return@pointerInput
                    val slideThresholdPx = 14.dp.toPx()

                    awaitPointerEventScope {
                        var downPos: Offset? = null
                        var isSliding = false
                        var lastAnnouncedId: String? = null

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.isEmpty()) {
                                if (isSliding) {
                                    event.changes.forEach { it.consume() }
                                }
                                downPos = null
                                isSliding = false
                                lastAnnouncedId = null
                            } else if (pressed.size >= 2) {
                                // Gesto multi-toque (bimanual): no interceptar como deslizamiento
                                downPos = null
                                isSliding = false
                                lastAnnouncedId = null
                            } else {
                                val change = pressed[0]
                                val pos = change.position

                                if (downPos == null) {
                                    downPos = pos
                                    isSliding = false
                                    lastAnnouncedId = null
                                } else {
                                    val dist = (pos - downPos!!).getDistance()
                                    if (!isSliding && dist > slideThresholdPx) {
                                        isSliding = true
                                    }

                                    if (isSliding) {
                                        change.consume()
                                        val hit = registry.findTargetAt(pos)
                                        if (hit != null) {
                                            if (hit.id != lastAnnouncedId) {
                                                lastAnnouncedId = hit.id
                                                hit.onHover?.invoke()
                                                currentOnExploreHover.value(hit.label)
                                            }
                                        } else {
                                            lastAnnouncedId = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}
