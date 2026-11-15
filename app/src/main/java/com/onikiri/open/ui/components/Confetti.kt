package com.onikiri.open.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CONFETTI_COLORS = listOf(
    Color(0xFFFF4D4D), Color(0xFFFF9F1C), Color(0xFFFFD93D), Color(0xFF4CD964),
    Color(0xFF1ED6C4), Color(0xFF3D8BFF), Color(0xFF9B5DE5), Color(0xFFFF5FA2)
)

private class Particle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var rx = 0f
    var ry = 0f
    var rz = 0f
    var vrx = 0f
    var vry = 0f
    var vrz = 0f
    var w = 0f
    var h = 0f
    var phase = 0f
    var age = 0f
    var resting = false
    var color: Color = Color.White
}

private const val LIFETIME = 7.5f
private const val FADE = 1.2f
private const val TWO_PI = (2.0 * PI).toFloat()

private fun wrapPi(angle: Float): Float {
    val a = (angle + PI.toFloat()) % TWO_PI
    return (if (a < 0f) a + TWO_PI else a) - PI.toFloat()
}

/**
 * Simple 3D confetti: every piece is a quad rotated around all three axes and drawn with
 * perspective. Gravity, air drag, terminal velocity, flutter, wall and floor collisions and
 * settling are simulated each frame.
 */
private class ConfettiSystem {
    val particles = ArrayList<Particle>()
    private val random = Random(System.nanoTime())

    private fun spawn(x: Float, y: Float, vx: Float, vy: Float, density: Float): Particle {
        val p = Particle()
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.w = (7f + random.nextFloat() * 8f) * density
        p.h = p.w * (1.3f + random.nextFloat() * 1.2f)
        p.rx = random.nextFloat() * TWO_PI
        p.ry = random.nextFloat() * TWO_PI
        p.rz = random.nextFloat() * TWO_PI
        p.vrx = (random.nextFloat() - 0.5f) * 24f
        p.vry = (random.nextFloat() - 0.5f) * 24f
        p.vrz = (random.nextFloat() - 0.5f) * 10f
        p.phase = random.nextFloat() * TWO_PI
        p.color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)]
        particles.add(p)
        return p
    }

    private fun cannon(x: Float, y: Float, centerDegrees: Float, count: Int, density: Float) {
        repeat(count) {
            val angle = Math.toRadians((centerDegrees + (random.nextFloat() - 0.5f) * 36f).toDouble())
            val speed = (950f + random.nextFloat() * 1150f) * density
            spawn(
                x + (random.nextFloat() - 0.5f) * 24f * density,
                y,
                (cos(angle) * speed).toFloat(),
                (-sin(angle) * speed).toFloat(),
                density
            )
        }
    }

    fun burst(width: Float, height: Float, density: Float) {
        cannon(0f, height, 62f, 110, density)
        cannon(width, height, 118f, 110, density)
    }

    fun rain(width: Float, density: Float) {
        repeat(70) {
            spawn(
                random.nextFloat() * width,
                -random.nextFloat() * 160f * density,
                (random.nextFloat() - 0.5f) * 220f * density,
                (80f + random.nextFloat() * 220f) * density,
                density
            )
        }
    }

    fun step(dt: Float, width: Float, height: Float, density: Float) {
        val gravity = 1500f * density
        val terminal = 600f * density
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += dt
            if (p.age > LIFETIME) {
                iterator.remove()
                continue
            }
            if (!p.resting) {
                p.vy += gravity * dt
                p.vx *= 1f - (1.8f * dt).coerceAtMost(0.9f)
                if (p.vy > terminal) p.vy += (terminal - p.vy) * (7f * dt).coerceAtMost(1f)
                p.vx += sin(p.age * 5f + p.phase) * 170f * density * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rx += p.vrx * dt
                p.ry += p.vry * dt
                p.rz += p.vrz * dt

                if (p.x < 0f) {
                    p.x = 0f
                    p.vx = abs(p.vx) * 0.5f
                } else if (p.x > width) {
                    p.x = width
                    p.vx = -abs(p.vx) * 0.5f
                }

                val floor = height - p.h * 0.15f
                if (p.y > floor) {
                    p.y = floor
                    if (abs(p.vy) > 160f * density) {
                        p.vy = -p.vy * 0.3f
                        p.vx *= 0.7f
                        p.vrx *= 0.5f
                        p.vry *= 0.5f
                    } else {
                        p.vy = 0f
                        p.resting = true
                    }
                }
            } else {
                p.vx *= (1f - 6f * dt).coerceAtLeast(0f)
                p.x += p.vx * dt
                // Lie flat: ease the tilt back to face the camera.
                p.rx += -wrapPi(p.rx) * (8f * dt).coerceAtMost(1f)
                p.ry += -wrapPi(p.ry) * (8f * dt).coerceAtMost(1f)
                p.rz += p.vrz * dt * 0.1f
            }
        }
    }
}

private fun DrawScope.drawParticle(p: Particle, path: Path, perspective: Float) {
    val cx = cos(p.rx)
    val sx = sin(p.rx)
    val cy = cos(p.ry)
    val sy = sin(p.ry)
    val cz = cos(p.rz)
    val sz = sin(p.rz)
    val hw = p.w / 2f
    val hh = p.h / 2f

    path.reset()
    for (i in 0 until 4) {
        val px = if (i == 0 || i == 3) -hw else hw
        val py = if (i < 2) -hh else hh
        // Rotate around X, then Y, then Z.
        val y1 = py * cx
        val z1 = py * sx
        val x2 = px * cy + z1 * sy
        val z2 = -px * sy + z1 * cy
        val x3 = x2 * cz - y1 * sz
        val y3 = x2 * sz + y1 * cz
        val scale = perspective / (perspective - z2).coerceAtLeast(perspective * 0.2f)
        val sxp = p.x + x3 * scale
        val syp = p.y + y3 * scale
        if (i == 0) path.moveTo(sxp, syp) else path.lineTo(sxp, syp)
    }
    path.close()

    val facing = abs(cx * cy)
    val shade = 0.58f + 0.42f * facing
    val alpha = ((LIFETIME - p.age) / FADE).coerceIn(0f, 1f)
    drawPath(
        path = path,
        color = Color(
            red = p.color.red * shade,
            green = p.color.green * shade,
            blue = p.color.blue * shade,
            alpha = alpha
        )
    )
}

/** Fires a confetti burst whenever [playKey] changes (and once when first shown). */
@Composable
fun Confetti3D(playKey: Int, modifier: Modifier = Modifier) {
    val system = remember { ConfettiSystem() }
    val path = remember { Path() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current.density

    LaunchedEffect(playKey, size) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        system.burst(width, height, density)
        var rained = false
        var elapsed = 0f
        var last = withFrameNanos { it }
        while (system.particles.isNotEmpty()) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.033f)
            last = now
            elapsed += dt
            if (!rained && elapsed > 0.45f) {
                rained = true
                system.rain(width, density)
            }
            system.step(dt, width, height, density)
            frame = now
        }
        frame += 1
    }

    Canvas(modifier = modifier.onSizeChanged { size = it }) {
        frame // read to redraw every frame
        val perspective = 900f * density
        for (p in system.particles) drawParticle(p, path, perspective)
    }
}
