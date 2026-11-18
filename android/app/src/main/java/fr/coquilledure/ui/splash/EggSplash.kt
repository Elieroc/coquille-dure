package fr.coquilledure.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.coquilledure.R
import fr.coquilledure.ui.theme.Figtree
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Animation de démarrage : l'œuf du splash système vacille, se fêle puis la calotte saute.
 * Les dessins viennent de tools/gen_egg.py (cadre 108 unités). L'œuf fait 288 dp de côté,
 * comme l'icône du splash système, pour une reprise sans saut.
 */

private val SplashGreen = Color(0xFF007A33)
private val CrackColor = Color(0xFF9C7A4E)
private val ShellColor = Color(0xFFFFF8EC)

private const val UNITS = 108f
private val EGG_SIZE = 288.dp

/** Points de la fêlure (identiques à ZZ[1:-1] de gen_egg.py). */
private val CRACK = listOf(
    36.5f to 52.5f, 41f to 58.5f, 46.5f to 51.5f, 52f to 58.5f, 57.5f to 51.5f,
    63f to 58.5f, 68.5f to 52f, 73f to 57f,
)

/** Pose finale de la calotte (LID_T de gen_egg.py). */
private const val LID_ROT = -22f
private const val LID_PX = 36.5f
private const val LID_PY = 52.5f
private const val LID_TX = 6f
private const val LID_TY = -8f

/** Éclats : triangle, point de départ (sur la fêlure) et position finale. */
private class Shard(val pts: List<Pair<Float, Float>>, val from: Pair<Float, Float>, val spin: Float)

private val SHARDS = listOf(
    Shard(listOf(27f to 44f, 31f to 41.5f, 30.2f to 46f), 38f to 54f, -160f),
    Shard(listOf(78.5f to 43f, 83f to 45.5f, 78.8f to 47.5f), 72f to 54f, 140f),
    Shard(listOf(33f to 36f, 35.2f to 33.8f, 35.8f to 37f), 44f to 52f, -220f),
)

@Composable
fun EggSplash(onFinished: () -> Unit) {
    val wobble = remember { Animatable(0f) }
    val crack = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val shards = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(80)
        // Vacillement : petits coups de plus en plus forts.
        for (angle in listOf(9f, -13f, 16f, -11f, 5f, 0f)) wobble.animateTo(angle, tween(105, easing = FastOutSlowInEasing))
        crack.animateTo(1f, tween(340, easing = LinearEasing))
        delay(130)
        coroutineScope {
            launch { lid.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
            launch { shards.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            launch { delay(220); title.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        }
        delay(300)
        fade.animateTo(0f, tween(300))
        onFinished()
    }

    val unit = with(LocalDensity.current) { EGG_SIZE.toPx() } / UNITS
    val open = crack.value >= 1f

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(SplashGreen),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(EGG_SIZE)
                .graphicsLayer {
                    rotationZ = wobble.value
                    transformOrigin = TransformOrigin(0.5f, 83f / UNITS)
                },
        ) {
            Image(painterResource(R.drawable.egg_shadow), null, Modifier.fillMaxSize())
            if (!open) {
                Image(painterResource(R.drawable.egg_whole), null, Modifier.fillMaxSize())
            } else {
                Image(painterResource(R.drawable.egg_inside), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.egg_bottom), null, Modifier.fillMaxSize())
                // Saut : la calotte monte au-delà de sa pose finale puis retombe (ressort).
                val p = lid.value
                val hop = kotlin.math.sin(p.coerceIn(0f, 1f) * Math.PI.toFloat()) * -6f
                Image(
                    painterResource(R.drawable.egg_lid), null,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(LID_PX / UNITS, LID_PY / UNITS)
                            rotationZ = LID_ROT * p
                            translationX = LID_TX * p * unit
                            translationY = (LID_TY * p + hop) * unit
                        },
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                // Fêlure qui se dessine de gauche à droite.
                if (crack.value > 0f && lid.value < 0.04f) {
                    val path = Path().apply {
                        CRACK.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * unit, y * unit) else lineTo(x * unit, y * unit) }
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * crack.value, part, true)
                    drawPath(part, CrackColor, style = Stroke(1.3f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // Éclats projetés depuis la fêlure.
                val s = shards.value
                if (s > 0f) {
                    SHARDS.forEach { shard ->
                        val cx = shard.pts.map { it.first }.average().toFloat()
                        val cy = shard.pts.map { it.second }.average().toFloat()
                        val dx = (shard.from.first - cx) * (1f - s)
                        val dy = (shard.from.second - cy) * (1f - s)
                        translate(dx * unit, dy * unit) {
                            rotate(shard.spin * (1f - s), Offset(cx * unit, cy * unit)) {
                                val tri = Path().apply {
                                    shard.pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * unit, y * unit) else lineTo(x * unit, y * unit) }
                                    close()
                                }
                                drawPath(tri, ShellColor, alpha = s.coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Coquille dure",
            color = Color.White,
            fontFamily = Figtree,
            fontWeight = FontWeight.Bold,
            fontSize = (34f / LocalDensity.current.fontScale).sp,
            modifier = Modifier
                .offset(y = 112.dp)
                .graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 24.dp.toPx()
                },
        )
    }
}
