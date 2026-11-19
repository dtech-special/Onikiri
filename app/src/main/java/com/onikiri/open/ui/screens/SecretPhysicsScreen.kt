package com.onikiri.open.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onikiri.open.R
import com.onikiri.open.ui.components.AppIcons
import kotlin.math.sqrt

// Onikiri logo (same path as the launcher icon), viewport 264 x 245.
private const val LOGO_PATH =
    "M121.54,0.99C128.41,-0.33 135.48,-0.33 142.35,0.99C144.92,1.49 147.41,2.26 149.84,3.3C139.27,8.52 132,19.41 132,32C132,49.67 146.33,64 164,64C165.15,64 166.28,63.94 167.4,63.82C165.21,68.94 164,74.58 164,80.5C164,103.97 183.03,123 206.5,123C215.37,123 223.6,120.28 230.42,115.63C256.74,161.72 269.07,187.17 261.86,207.99C259.57,214.6 256.04,220.73 251.46,226.02C239.09,240.29 216.6,243.83 177,244.71V207C177,191.92 177,184.37 172.31,179.69C167.63,175 160.09,175 145,175H119C103.92,175 96.37,175 91.69,179.69C87,184.37 87,191.92 87,207V244.71C47.33,243.84 24.82,240.3 12.44,226.02C7.85,220.73 4.32,214.6 2.03,207.99C-6.19,184.25 10.99,154.5 45.34,95C79.7,35.5 96.87,5.75 121.54,0.99Z"
private const val LOGO_W = 264f
private const val LOGO_H = 245f
private const val BODY_COUNT = 5
private const val RESTITUTION_WALL = 0.75f
private const val RESTITUTION_BODY = 0.9f
private const val MAX_SPEED = 6000f

private class Body {
    var placed = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var grabbed = false
    var tx = 0f
    var ty = 0f
}

/**
 * Easter egg: five white Onikiri logos on a black screen. They fall towards the side the phone is
 * tilted to (accelerometer), bounce off the edges and each other and can be dragged and thrown.
 * Everything is drawn in one Canvas and driven by withFrameNanos, so there are no recompositions per frame.
 */
@Composable
fun SecretPhysicsScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val density = LocalDensity.current
    val logoPx = with(density) { 100.dp.toPx() }
    val radius = logoPx * 0.46f
    val gravityScale = 170f * density.density // px/s^2 per m/s^2

    val logo = remember { PathParser().parsePathString(LOGO_PATH).toPath() }
    val bodies = remember { List(BODY_COUNT) { Body() } }
    val gravity = remember { floatArrayOf(0f, 0f) } // screen-space acceleration in m/s^2
    val frame = remember { mutableLongStateOf(0L) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The sensor reports the reaction to gravity, so the x axis is mirrored.
                gravity[0] = -event.values[0]
                gravity[1] = event.values[1]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            gravity[1] = 9.81f
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    LaunchedEffect(size, radius) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        bodies.forEachIndexed { i, b ->
            if (!b.placed) {
                b.placed = true
                b.x = w * (i + 1) / (BODY_COUNT + 1)
                b.y = h * 0.3f + (i % 2) * radius * 2.2f
                b.vx = (i - 2) * 60f
            }
            b.x = b.x.coerceIn(radius, w - radius)
            b.y = b.y.coerceIn(radius, h - radius)
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 1f / 30f)
                last = now
                val sub = dt / 2f
                repeat(2) {
                    stepPhysics(bodies, w, h, radius, gravity[0] * gravityScale, gravity[1] * gravityScale, sub)
                }
                frame.longValue = now
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .onSizeChanged { size = it }
            .pointerInput(radius) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val reach = radius * 1.35f
                    var hit: Body? = null
                    var best = reach * reach
                    for (b in bodies) {
                        if (!b.placed) continue
                        val dx = b.x - down.position.x
                        val dy = b.y - down.position.y
                        val d = dx * dx + dy * dy
                        if (d <= best) {
                            best = d
                            hit = b
                        }
                    }
                    val body = hit ?: return@awaitEachGesture
                    body.grabbed = true
                    body.tx = down.position.x
                    body.ty = down.position.y
                    body.vx = 0f
                    body.vy = 0f
                    try {
                        drag(down.id) { change ->
                            body.tx = change.position.x
                            body.ty = change.position.y
                            change.consume()
                        }
                    } finally {
                        body.grabbed = false
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reading the frame counter here invalidates only the draw phase, never composition.
            if (frame.longValue == Long.MIN_VALUE) return@Canvas
            val s = logoPx / LOGO_W
            for (b in bodies) {
                if (!b.placed) continue
                withTransform({
                    translate(b.x, b.y)
                    scale(s, s, Offset.Zero)
                    translate(-LOGO_W / 2f, -LOGO_H / 2f)
                }) {
                    drawPath(logo, Color.White)
                }
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                painter = AppIcons.close(),
                contentDescription = stringResource(R.string.install_close),
                tint = Color.White
            )
        }
    }
}

private fun stepPhysics(
    bodies: List<Body>,
    w: Float,
    h: Float,
    r: Float,
    gx: Float,
    gy: Float,
    dt: Float
) {
    for (b in bodies) {
        if (!b.placed) continue
        if (b.grabbed) {
            val nx = b.tx.coerceIn(r, w - r)
            val ny = b.ty.coerceIn(r, h - r)
            b.vx = b.vx * 0.5f + (nx - b.x) / dt * 0.5f
            b.vy = b.vy * 0.5f + (ny - b.y) / dt * 0.5f
            limitSpeed(b)
            b.x = nx
            b.y = ny
            continue
        }
        b.vx += gx * dt
        b.vy += gy * dt
        val drag = 1f - 0.2f * dt
        b.vx *= drag
        b.vy *= drag
        limitSpeed(b)
        b.x += b.vx * dt
        b.y += b.vy * dt
        bounceOffWalls(b, w, h, r)
    }
    for (i in bodies.indices) {
        val a = bodies[i]
        if (!a.placed) continue
        for (j in i + 1 until bodies.size) {
            val b = bodies[j]
            if (!b.placed) continue
            collide(a, b, r)
        }
    }
    for (b in bodies) if (b.placed && !b.grabbed) bounceOffWalls(b, w, h, r)
}

private fun limitSpeed(b: Body) {
    val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
    if (speed > MAX_SPEED) {
        val k = MAX_SPEED / speed
        b.vx *= k
        b.vy *= k
    }
}

private fun bounceOffWalls(b: Body, w: Float, h: Float, r: Float) {
    if (b.x < r) {
        b.x = r
        b.vx = -b.vx * RESTITUTION_WALL
    } else if (b.x > w - r) {
        b.x = w - r
        b.vx = -b.vx * RESTITUTION_WALL
    }
    if (b.y < r) {
        b.y = r
        b.vy = -b.vy * RESTITUTION_WALL
    } else if (b.y > h - r) {
        b.y = h - r
        b.vy = -b.vy * RESTITUTION_WALL
    }
}

/** Elastic collision of two equal circles; a dragged logo behaves as an immovable one. */
private fun collide(a: Body, b: Body, r: Float) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val distSq = dx * dx + dy * dy
    val minDist = 2f * r
    if (distSq >= minDist * minDist) return
    val dist = sqrt(distSq).coerceAtLeast(0.0001f)
    val nx = if (distSq == 0f) 1f else dx / dist
    val ny = if (distSq == 0f) 0f else dy / dist
    val invA = if (a.grabbed) 0f else 1f
    val invB = if (b.grabbed) 0f else 1f
    val total = invA + invB
    if (total == 0f) return
    val overlap = minDist - dist
    a.x -= nx * overlap * (invA / total)
    a.y -= ny * overlap * (invA / total)
    b.x += nx * overlap * (invB / total)
    b.y += ny * overlap * (invB / total)
    val closing = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
    if (closing > 0f) {
        val impulse = (1f + RESTITUTION_BODY) * closing / total
        a.vx -= impulse * invA * nx
        a.vy -= impulse * invA * ny
        b.vx += impulse * invB * nx
        b.vy += impulse * invB * ny
    }
}
