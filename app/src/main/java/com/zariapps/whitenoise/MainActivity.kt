package com.zariapps.whitenoise

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ── Colors ─────────────────────────────────────────────────────────────────
private val BgColor         = Color(0xFF060B1A)
private val SurfaceColor    = Color(0xFF0D1830)
private val SurfaceSelected = Color(0xFF142A4A)
private val AccentColor     = Color(0xFF4DA8FF)
private val AccentDark      = Color(0xFF1A5FA8)
private val TextPrimary     = Color(0xFFCCDFFF)
private val TextSecondary   = Color(0xFF5A7299)
private val BorderSelected  = Color(0xFF3D8BE0)

// ── Sound types ────────────────────────────────────────────────────────────
enum class SoundType(val label: String, val icon: String, val description: String) {
    WHITE   ("White Noise",  "🌫",  "Pure static for deep sleep"),
    PINK    ("Pink Noise",   "🌸",  "Soft balanced frequencies"),
    BROWN   ("Brown Noise",  "🍂",  "Deep low-frequency rumble"),
    FAN     ("Fan",          "🌀",  "Steady airflow ambience"),
    RAIN    ("Rain",         "🌧",  "Gentle rainfall"),
    OCEAN   ("Ocean",        "🌊",  "Rolling waves"),
    FOREST  ("Forest",       "🌲",  "Rustling leaves & wind"),
    HEARTBT ("Heartbeat",    "💗",  "Soothing rhythmic pulse")
}

// ── Audio generators ───────────────────────────────────────────────────────
class PinkNoiseGen {
    private val b = FloatArray(7)
    fun next(): Float {
        val w = Random.nextFloat() * 2f - 1f
        b[0] = 0.99886f * b[0] + w * 0.0555179f
        b[1] = 0.99332f * b[1] + w * 0.0750759f
        b[2] = 0.96900f * b[2] + w * 0.1538520f
        b[3] = 0.86650f * b[3] + w * 0.3104856f
        b[4] = 0.55000f * b[4] + w * 0.5329522f
        b[5] = -0.7616f * b[5] - w * 0.0168980f
        val pink = b[0] + b[1] + b[2] + b[3] + b[4] + b[5] + b[6] + w * 0.5362f
        b[6] = w * 0.115926f
        return (pink * 0.11f).coerceIn(-1f, 1f)
    }
}

class BrownNoiseGen {
    private var last = 0f
    fun next(): Float {
        last = (last + (Random.nextFloat() * 2f - 1f) * 0.02f) / 1.02f
        return (last * 3.5f).coerceIn(-1f, 1f)
    }
}

class FanGen {
    private var f1 = 0f; private var f2 = 0f
    fun next(): Float {
        val w = Random.nextFloat() * 2f - 1f
        f1 = 0.992f * f1 + 0.008f * w
        f2 = 0.990f * f2 + 0.010f * f1
        return (f2 * 6f).coerceIn(-1f, 1f)
    }
}

class RainGen {
    private var f = 0f
    fun next(): Float {
        f = 0.944f * f + 0.056f * (Random.nextFloat() * 2f - 1f)
        return (f * 2.8f).coerceIn(-1f, 1f)
    }
}

class OceanGen {
    private var phase = 0.0; private var f = 0f
    private val SR = 44100.0
    fun next(): Float {
        phase += 2 * PI * 0.12 / SR
        if (phase > 2 * PI) phase -= 2 * PI
        val env = (sin(phase) * 0.45 + 0.55).toFloat()
        f = 0.9985f * f + 0.0015f * (Random.nextFloat() * 2f - 1f)
        return (f * env * 5f).coerceIn(-1f, 1f)
    }
}

class ForestGen {
    private var f1 = 0f; private var f2 = 0f; private var modPhase = 0.0
    private val SR = 44100.0
    fun next(): Float {
        modPhase += 2 * PI * 0.18 / SR
        if (modPhase > 2 * PI) modPhase -= 2 * PI
        val mod = (sin(modPhase) * 0.35 + 0.65).toFloat()
        val w = Random.nextFloat() * 2f - 1f
        f1 = 0.996f * f1 + 0.004f * w
        f2 = 0.994f * f2 + 0.006f * f1
        return (f2 * mod * 5f).coerceIn(-1f, 1f)
    }
}

class HeartbeatGen(bpm: Float = 64f) {
    private val SR = 44100
    private val period = (SR * 60f / bpm).toInt()
    private var pos = 0; private var f1 = 0f; private var f2 = 0f
    private val lub = (SR * 0.055f).toInt()
    private val dubOffset = (SR * 0.13f).toInt()
    private val dub = (SR * 0.045f).toInt()
    fun next(): Float {
        val p = pos % period; pos++
        val lubV = if (p < lub) sin(p.toFloat() / lub * PI.toFloat()).pow(2) else 0f
        val dp = p - dubOffset
        val dubV = if (dp in 0 until dub) sin(dp.toFloat() / dub * PI.toFloat()).pow(2) * 0.7f else 0f
        val impulse = lubV + dubV
        f1 = 0.975f * f1 + 0.025f * impulse
        f2 = 0.972f * f2 + 0.028f * f1
        return (f2 * 4f).coerceIn(-1f, 1f)
    }
}

// ── Sound engine ───────────────────────────────────────────────────────────
class SoundEngine {
    private val SR = 44100
    private val CHUNK = 2048
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    private var pink   = PinkNoiseGen()
    private var brown  = BrownNoiseGen()
    private var fan    = FanGen()
    private var rain   = RainGen()
    private var ocean  = OceanGen()
    private var forest = ForestGen()
    private var heart  = HeartbeatGen()

    fun start(type: SoundType, volume: Float) {
        stop()
        pink = PinkNoiseGen(); brown = BrownNoiseGen(); fan = FanGen()
        rain = RainGen(); ocean = OceanGen(); forest = ForestGen(); heart = HeartbeatGen()
        running = true

        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBuf, CHUNK * 2),
            AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track?.setVolume(volume)
        track?.play()

        thread = Thread {
            val buf = ShortArray(CHUNK)
            while (running) {
                for (i in buf.indices) {
                    val s = when (type) {
                        SoundType.WHITE  -> Random.nextFloat() * 2f - 1f
                        SoundType.PINK   -> pink.next()
                        SoundType.BROWN  -> brown.next()
                        SoundType.FAN    -> fan.next()
                        SoundType.RAIN   -> rain.next()
                        SoundType.OCEAN  -> ocean.next()
                        SoundType.FOREST -> forest.next()
                        SoundType.HEARTBT-> heart.next()
                    }
                    buf[i] = (s * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                if (running) track?.write(buf, 0, CHUNK)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        track?.stop(); track?.release(); track = null
        try { thread?.join(300) } catch (_: Exception) {}
        thread = null
    }

    fun setVolume(v: Float) { track?.setVolume(v) }
}

// ── Activity ──────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WhiteNoiseApp() }
    }
}

// ── Timer options ─────────────────────────────────────────────────────────
private val TIMER_OPTIONS = listOf(0, 15, 30, 45, 60, 120) // minutes; 0 = off

// ── Root composable ───────────────────────────────────────────────────────
@Composable
fun WhiteNoiseApp() {
    val engine    = remember { SoundEngine() }
    val scope     = rememberCoroutineScope()

    var selected  by remember { mutableStateOf<SoundType?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var volume    by remember { mutableFloatStateOf(0.7f) }
    var timerMin  by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(0) } // seconds

    // Timer countdown
    LaunchedEffect(isPlaying, remaining) {
        if (isPlaying && remaining > 0) {
            delay(1000)
            remaining--
            if (remaining == 0) {
                isPlaying = false
                engine.stop()
            }
        }
    }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    fun play(type: SoundType) {
        selected = type
        isPlaying = true
        if (timerMin > 0) remaining = timerMin * 60
        engine.start(type, volume)
    }

    fun pause() {
        isPlaying = false
        engine.stop()
    }

    fun resume() {
        selected?.let { play(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgColor, Color(0xFF080E20), BgColor)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🌙  White Noise", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Sleep sounds & relaxation", color = TextSecondary, fontSize = 13.sp)
            }

            // Sound grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(SoundType.entries) { type ->
                    SoundCard(
                        type      = type,
                        isSelected = type == selected,
                        isPlaying  = type == selected && isPlaying,
                        onClick   = {
                            if (type == selected && isPlaying) pause()
                            else play(type)
                        }
                    )
                }
            }

            // Volume
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (volume < 0.1f) "🔇" else if (volume < 0.5f) "🔉" else "🔊", fontSize = 18.sp)
                    Text("Volume", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${(volume * 100).toInt()}%", color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it; engine.setVolume(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = AccentColor,
                        activeTrackColor = AccentColor,
                        inactiveTrackColor = SurfaceSelected
                    )
                )
            }

            // Sleep timer
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⏱", fontSize = 16.sp)
                    Text("Sleep Timer", color = TextSecondary, fontSize = 13.sp)
                    if (isPlaying && remaining > 0) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatRemaining(remaining),
                            color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIMER_OPTIONS.forEach { min ->
                        val selected = timerMin == min
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) AccentColor else SurfaceColor)
                                .clickable {
                                    timerMin = min
                                    remaining = if (isPlaying && min > 0) min * 60 else 0
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (min == 0) "Off" else if (min < 60) "${min}m" else "${min / 60}h",
                                color = if (selected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Play / Pause button
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying)
                                Brush.radialGradient(listOf(AccentColor, AccentDark))
                            else
                                Brush.radialGradient(listOf(SurfaceSelected, SurfaceColor))
                        )
                        .clickable {
                            if (isPlaying) pause() else resume()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 28.sp)
                }
            }

            // Now playing label
            if (selected != null) {
                val label = if (isPlaying) "Now playing: ${selected!!.label}" else "Paused: ${selected!!.label}"
                Text(
                    label,
                    color = if (isPlaying) AccentColor else TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Sound card ────────────────────────────────────────────────────────────
@Composable
fun SoundCard(
    type: SoundType,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.97f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) SurfaceSelected else SurfaceColor,
        animationSpec = tween(200)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .then(
                if (isSelected) Modifier.border(1.5.dp, BorderSelected.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(type.icon, fontSize = 28.sp)
                if (isPlaying) AnimatedBars()
            }
            Text(type.label, color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            Text(type.description, color = TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

// ── Animated bars (playing indicator) ────────────────────────────────────
@Composable
fun AnimatedBars() {
    val inf = rememberInfiniteTransition(label = "bars")
    val h1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse,
        StartOffset(0)), label = "b1")
    val h2 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse,
        StartOffset(150)), label = "b2")
    val h3 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse,
        StartOffset(300)), label = "b3")

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(16.dp)
    ) {
        listOf(h1, h2, h3).forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentColor)
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────
fun formatRemaining(secs: Int): String {
    val m = secs / 60; val s = secs % 60
    return if (m > 0) "${m}m ${s.toString().padStart(2, '0')}s" else "${s}s"
}
