package id.midea.remote

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

// ---------- Palet warna per mode ----------

private class Palette(val top: Color, val bottom: Color, val glow: Color)

private fun paletteFor(s: MideaIr.State): Palette =
    if (!s.power) Palette(Color(0xFF263040), Color(0xFF0C1117), Color(0xFF8493A4))
    else when (s.mode) {
        MideaIr.Mode.COOL -> Palette(Color(0xFF2086FF), Color(0xFF071A55), Color(0xFFC8F4FF))
        MideaIr.Mode.DRY -> Palette(Color(0xFF14CDAA), Color(0xFF04414A), Color(0xFFD6FFF3))
        MideaIr.Mode.AUTO -> Palette(Color(0xFFFF7B5A), Color(0xFF47155A), Color(0xFFFFE6BC))
        MideaIr.Mode.FAN -> Palette(Color(0xFF9D66FF), Color(0xFF231366), Color(0xFFEFE2FF))
    }

private fun modeLabel(m: MideaIr.Mode) = when (m) {
    MideaIr.Mode.COOL -> "Dingin"
    MideaIr.Mode.DRY -> "Kering"
    MideaIr.Mode.AUTO -> "Otomatis"
    MideaIr.Mode.FAN -> "Kipas"
}

private fun modeIcon(m: MideaIr.Mode) = when (m) {
    MideaIr.Mode.COOL -> "❄️"
    MideaIr.Mode.DRY -> "💧"
    MideaIr.Mode.AUTO -> "✨"
    MideaIr.Mode.FAN -> "🌀"
}

private fun fanLabel(f: MideaIr.Fan) = when (f) {
    MideaIr.Fan.AUTO -> "Auto"
    MideaIr.Fan.LOW -> "Rendah"
    MideaIr.Fan.MED -> "Sedang"
    MideaIr.Fan.HIGH -> "Tinggi"
}

private fun timerLabel(min: Int): String = when {
    min <= 0 -> "Tidak aktif"
    min < 60 -> "$min menit"
    min % 60 == 0 -> "${min / 60} jam"
    else -> "${min / 60} jam ${min % 60} menit"
}

private class Feature(
    val key: String, val icon: String, val label: String,
    val code: Long, val offCode: Long? = null
)

private val features = listOf(
    Feature("swing", "↕️", "Ayun", MideaIr.SWING),
    Feature("turbo", "🚀", "Turbo", MideaIr.TURBO),
    Feature("eco", "🌿", "Eco", MideaIr.ECO),
    Feature("sleep", "🌙", "Tidur", 0L),
    Feature("light", "💡", "Lampu", MideaIr.LIGHT),
    Feature("quiet", "🤫", "Senyap", MideaIr.QUIET_ON, MideaIr.QUIET_OFF),
    Feature("clean", "🧼", "Bersih", MideaIr.SELF_CLEAN)
)

private fun loadState(p: SharedPreferences) = MideaIr.State(
    power = p.getBoolean("power", false),
    mode = MideaIr.Mode.values().getOrElse(p.getInt("mode", 0)) { MideaIr.Mode.COOL },
    fan = MideaIr.Fan.values().getOrElse(p.getInt("fan", 0)) { MideaIr.Fan.AUTO },
    tempC = p.getInt("temp", 25).coerceIn(17, 30),
    sleep = p.getBoolean("sleep", false),
    offTimerMin = p.getInt("timer", 0)
)

private fun saveState(p: SharedPreferences, s: MideaIr.State) {
    p.edit()
        .putBoolean("power", s.power)
        .putInt("mode", s.mode.ordinal)
        .putInt("fan", s.fan.ordinal)
        .putInt("temp", s.tempC)
        .putBoolean("sleep", s.sleep)
        .putInt("timer", s.offTimerMin)
        .apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val sender = IrSender(this)
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        setContent { RemoteApp(sender, prefs) }
    }
}

// ---------- Layar utama ----------

@Composable
fun RemoteApp(sender: IrSender, prefs: SharedPreferences) {
    var st by remember { mutableStateOf(loadState(prefs)) }
    var flags by remember { mutableStateOf<Set<String>>(prefs.getStringSet("flags", emptySet()) ?: emptySet()) }
    var info by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val pal = paletteFor(st)
    val top by animateColorAsState(pal.top, tween(700), label = "top")
    val bottom by animateColorAsState(pal.bottom, tween(700), label = "bottom")
    val glow by animateColorAsState(pal.glow, tween(700), label = "glow")

    fun report(ok: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        info = if (ok) "Sinyal terkirim" else "Gagal mengirim sinyal IR"
    }

    fun push(next: MideaIr.State) {
        st = next
        saveState(prefs, next)
        report(sender.send(MideaIr.commandBytes(next)))
    }

    // Seperti remote asli: saat AC mati, pengaturan hanya berubah di layar.
    fun change(next: MideaIr.State) {
        if (st.power) push(next) else {
            st = next
            saveState(prefs, next)
        }
    }

    fun toggleFeature(f: Feature) {
        if (!st.power) {
            info = "Nyalakan AC dulu"
            return
        }
        if (f.key == "sleep") {
            push(st.copy(sleep = !st.sleep))
            return
        }
        val wasOn = f.key in flags
        flags = if (wasOn) flags - f.key else flags + f.key
        prefs.edit().putStringSet("flags", flags).apply()
        val code = if (wasOn && f.offCode != null) f.offCode else f.code
        report(sender.send(MideaIr.fromLong(code)))
    }

    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Midea 2 PK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("MSAF-18CRN2X", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                    Text(info, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }

                if (!sender.available) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "HP ini tidak mendeteksi pemancar IR, jadi sinyal tidak bisa dikirim ke AC.",
                        color = Color(0xFFFFC2B8), fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(8.dp))

                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    AcScene(st, "swing" in flags, glow, Modifier.fillMaxSize())
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundBtn("−", 60.dp) { if (st.tempC > 17) change(st.copy(tempC = st.tempC - 1)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${st.tempC}°", color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Light)
                            Text(
                                if (st.power) "${modeLabel(st.mode)}, kipas ${fanLabel(st.fan)}" else "AC mati",
                                color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp
                            )
                        }
                        RoundBtn("+", 60.dp) { if (st.tempC < 30) change(st.copy(tempC = st.tempC + 1)) }
                    }
                }

                PowerButton(st.power, glow, bottom) { push(st.copy(power = !st.power)) }

                Spacer(Modifier.height(8.dp))

                SectionLabel("Mode")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MideaIr.Mode.COOL, MideaIr.Mode.DRY, MideaIr.Mode.AUTO, MideaIr.Mode.FAN).forEach { m ->
                        Pill(modeIcon(m), modeLabel(m), st.mode == m, bottom, Modifier.weight(1f)) {
                            change(st.copy(mode = m))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                SectionLabel("Kecepatan kipas")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MideaIr.Fan.AUTO to "🔁", MideaIr.Fan.LOW to "▂",
                        MideaIr.Fan.MED to "▂▄", MideaIr.Fan.HIGH to "▂▄▆"
                    ).forEach { (f, icon) ->
                        Pill(icon, fanLabel(f), st.fan == f, bottom, Modifier.weight(1f)) {
                            change(st.copy(fan = f))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                SectionLabel("Fitur")
                features.chunked(4).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { f ->
                            val selected = st.power && (if (f.key == "sleep") st.sleep else f.key in flags)
                            Pill(f.icon, f.label, selected, bottom, Modifier.weight(1f)) { toggleFeature(f) }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("⏱  Timer mati", color = Color.White, fontSize = 15.sp)
                        Text(
                            timerLabel(st.offTimerMin),
                            color = Color.White.copy(alpha = if (st.offTimerMin > 0) 1f else 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundBtn("−", 44.dp) { change(st.copy(offTimerMin = (st.offTimerMin - 30).coerceAtLeast(0))) }
                        Spacer(Modifier.width(10.dp))
                        RoundBtn("+", 44.dp) { change(st.copy(offTimerMin = (st.offTimerMin + 30).coerceAtMost(1440))) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Status tombol fitur hanya perkiraan. Jika tidak sama dengan AC, ketuk sekali lagi.",
                    color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ---------- Ilustrasi AC dengan angin ----------

private val seedX = FloatArray(14) { ((it * 37) % 100) / 100f }
private val seedO = FloatArray(14) { ((it * 53 + 11) % 100) / 100f }

@Composable
private fun AcScene(state: MideaIr.State, swingOn: Boolean, glow: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scene")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing)),
        label = "t"
    )
    val power = state.power
    val windAlpha by animateFloatAsState(if (power) 1f else 0f, tween(500), label = "wind")
    val flapBase by animateFloatAsState(if (power) 26f else 0f, tween(600), label = "flap")

    // Bilangan bulat supaya animasi mulus saat berulang
    val k = when (state.fan) {
        MideaIr.Fan.LOW -> 1
        MideaIr.Fan.AUTO, MideaIr.Fan.MED -> 2
        MideaIr.Fan.HIGH -> 4
    }
    val streams = when (state.fan) {
        MideaIr.Fan.LOW -> 3
        MideaIr.Fan.AUTO, MideaIr.Fan.MED -> 4
        MideaIr.Fan.HIGH -> 6
    }
    val pk = if (k >= 4) 2 else 1

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.9f
        val bodyH = 98.dp.toPx()
        val left = (w - bodyW) / 2f
        val top = 6.dp.toPx()

        // Bayangan dan badan AC
        drawRoundRect(
            Color.Black.copy(alpha = 0.22f),
            Offset(left, top + 8.dp.toPx()), Size(bodyW, bodyH), CornerRadius(32.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFD9E4EF)), startY = top, endY = top + bodyH),
            topLeft = Offset(left, top), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(32.dp.toPx())
        )
        // Garis dekorasi dan lampu indikator
        drawRoundRect(
            Color(0xFFB9C8D8),
            Offset(left + 30.dp.toPx(), top + 24.dp.toPx()), Size(60.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx())
        )
        drawCircle(
            if (power) glow else Color(0xFF9AA7B4), 5.dp.toPx(),
            Offset(left + bodyW - 32.dp.toPx(), top + 27.dp.toPx())
        )

        // Celah angin dan sirip
        val ventW = bodyW * 0.8f
        val ventLeft = (w - ventW) / 2f
        val ventY = top + bodyH - 24.dp.toPx()
        drawRoundRect(
            Color(0xFF8DA2B8),
            Offset(ventLeft, ventY), Size(ventW, 12.dp.toPx()), CornerRadius(6.dp.toPx())
        )
        val angle = flapBase + if (swingOn && power) 12f * sin(2f * PI.toFloat() * t) else 0f
        rotate(degrees = angle, pivot = Offset(ventLeft, ventY + 8.dp.toPx())) {
            drawRoundRect(
                Color(0xFFC9D6E3),
                Offset(ventLeft, ventY + 5.dp.toPx()), Size(ventW, 7.dp.toPx()), CornerRadius(3.5f.dp.toPx())
            )
        }

        if (windAlpha > 0.01f) {
            val pivotY = ventY + 12.dp.toPx()
            val reach = h - pivotY - 4.dp.toPx()

            // Cahaya lembut di bawah AC
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(glow.copy(alpha = 0.32f * windAlpha), Color.Transparent),
                    startY = pivotY, endY = h
                ),
                topLeft = Offset(ventLeft, pivotY), size = Size(ventW, reach)
            )

            // Aliran angin
            val dash = 26.dp.toPx()
            val gapD = 22.dp.toPx()
            val period = dash + gapD
            for (i in 0 until streams) {
                val fx = (i + 0.5f) / streams
                val x0 = ventLeft + fx * ventW
                val dir = if (i % 2 == 0) 1f else -1f
                val sway = 16.dp.toPx() * dir
                val spread = (fx - 0.5f) * 70.dp.toPx()
                val path = Path().apply {
                    moveTo(x0, pivotY)
                    cubicTo(
                        x0 + sway, pivotY + reach * 0.3f,
                        x0 - sway, pivotY + reach * 0.65f,
                        x0 + spread, pivotY + reach
                    )
                }
                val phase = period - ((t * k * period + i * 9.dp.toPx()) % period)
                drawPath(
                    path,
                    glow.copy(alpha = 0.9f * windAlpha),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gapD), phase)
                    )
                )
            }

            // Partikel: salju (dingin), tetes (kering), kilau (lainnya)
            for (j in 0 until 14) {
                val yf = (t * pk + seedO[j]) % 1f
                val y = pivotY + 8.dp.toPx() + yf * (reach - 8.dp.toPx())
                val x = ventLeft + seedX[j] * ventW + sin(yf * 6.2832f + seedO[j] * 6f) * 10.dp.toPx()
                val c = glow.copy(alpha = (0.9f * (1f - yf) * windAlpha).coerceIn(0f, 1f))
                val r = (2f + seedO[j] * 2.5f).dp.toPx()
                when (state.mode) {
                    MideaIr.Mode.COOL -> {
                        drawLine(c, Offset(x - r, y), Offset(x + r, y), 1.5f.dp.toPx(), StrokeCap.Round)
                        drawLine(c, Offset(x, y - r), Offset(x, y + r), 1.5f.dp.toPx(), StrokeCap.Round)
                    }
                    MideaIr.Mode.DRY -> drawCircle(c, r * 1.2f, Offset(x, y))
                    else -> drawCircle(c, r * 0.8f, Offset(x, y))
                }
            }
        }
    }
}

// ---------- Komponen kecil ----------

@Composable
private fun PowerButton(on: Boolean, glow: Color, iconColor: Color, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing)),
        label = "p"
    )
    val fg = if (on) iconColor else Color.White
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (on) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    glow.copy(alpha = 0.4f * (1f - p)),
                    radius = size.minDimension / 2f * (0.62f + 0.38f * p)
                )
            }
        }
        Box(
            Modifier
                .size(98.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(38.dp)) {
                val sw = 4.5f.dp.toPx()
                val w = size.width
                drawArc(
                    color = fg, startAngle = -50f, sweepAngle = 280f, useCenter = false,
                    topLeft = Offset(sw, sw * 1.5f), size = Size(w - 2 * sw, w - 2 * sw - sw * 0.5f),
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
                drawLine(fg, Offset(w / 2, sw / 2), Offset(w / 2, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
private fun Pill(
    icon: String, label: String, selected: Boolean, textOnSelected: Color,
    modifier: Modifier, onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) Color.White else Color.White.copy(alpha = 0.14f), tween(250), label = "pillBg"
    )
    val scale by animateFloatAsState(
        if (selected) 1.04f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "pillScale"
    )
    Column(
        modifier
            .scale(scale)
            .height(66.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 20.sp)
        Text(
            label,
            color = if (selected) textOnSelected else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun RoundBtn(label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = (size.value * 0.44f).sp)
    }
}
