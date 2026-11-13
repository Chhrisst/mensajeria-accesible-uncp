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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mensajeria.domain.service.VibrationService

data class ExploreTarget(
    val id: String,
    val label: String,
    val bounds: Rect,
    val onHover: (() -> Unit)? = null
)

class TouchExploreRegistry {
    private val targets = LinkedHashMap<String, LayoutCoordinates>()
    private val labelProviders = LinkedHashMap<String, () -> String>()
    private val onHovers = LinkedHashMap<String, () -> (() -> Unit)?>()
    var hostCoordinates: LayoutCoordinates? = null

    fun register(
        id: String,
        labelProvider: () -> String,
        coordinates: LayoutCoordinates,
        onHover: () -> (() -> Unit)?
    ) {
        targets[id] = coordinates
        labelProviders[id] = labelProvider
        onHovers[id] = onHover
    }

    fun unregister(id: String) {
        targets.remove(id)
        labelProviders.remove(id)
        onHovers.remove(id)
    }

    fun clear() {
        targets.clear()
        labelProviders.clear()
        onHovers.clear()
    }

    fun findTargetAt(position: Offset): ExploreTarget? {
        val host = hostCoordinates ?: return null
        if (!host.isAttached) return null

        var bestTarget: ExploreTarget? = null
        var minArea = Float.MAX_VALUE

        for ((id, coords) in targets) {
            if (coords.isAttached) {
                val bounds = try {
                    host.localBoundingBoxOf(coords)
                } catch (_: Exception) {
                    null
                } ?: continue

                if (bounds.contains(position)) {
                    val area = bounds.width * bounds.height
                    if (area in 1f..minArea) {
                        minArea = area
                        bestTarget = ExploreTarget(
                            id = id,
                            label = labelProviders[id]?.invoke().orEmpty(),
                            bounds = bounds,
                            onHover = onHovers[id]?.invoke()
                        )
                    }
                }
            }
        }
        return bestTarget
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
                    labelProvider = { currentLabel.value },
                    coordinates = coordinates,
                    onHover = { currentOnHover.value }
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
    val context = LocalContext.current
    val registry = remember(screenKey) { TouchExploreRegistry() }
    val currentOnExploreHover = rememberUpdatedState(onExploreHover)

    CompositionLocalProvider(LocalTouchExploreRegistry provides registry) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached) {
                        registry.hostCoordinates = coordinates
                    }
                }
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
                                                try {
                                                    VibrationService.getInstance(context).vibrateHover()
                                                } catch (_: Exception) {}
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
