package com.example.jetnews.ui

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min

data class FxInfo(val bandCount: Int, val minLevel: Int, val maxLevel: Int, val centerFreqsHz: List<Int>)

data class FxSettings(
    val enabled: Boolean = false,
    val bands: List<Int> = emptyList(),
    val bass: Int = 0,
    val virtualizer: Int = 0,
    val preset: String = "Flat"
)

/** Owns the platform audio effects attached to the player's audio session. */
object AudioFx {
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virt: Virtualizer? = null
    private var appContext: Context? = null

    var info by mutableStateOf<FxInfo?>(null)
        private set
    var settings by mutableStateOf(FxSettings())
        private set

    val presets: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(0.8f, 0.5f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0.5f, 0.8f),
        "Vocal" to listOf(-0.3f, 0f, 0.5f, 0.4f, -0.1f),
        "Rock" to listOf(0.5f, 0.3f, -0.2f, 0.3f, 0.6f),
        "Pop" to listOf(-0.1f, 0.3f, 0.5f, 0.2f, -0.2f),
        "Jazz" to listOf(0.3f, 0.2f, -0.2f, 0.2f, 0.5f),
        "Classical" to listOf(0.4f, 0.3f, -0.2f, 0.3f, 0.4f)
    )

    private fun prefs(context: Context) = context.getSharedPreferences("fx_prefs", Context.MODE_PRIVATE)

    private fun loadPrefs(context: Context): FxSettings {
        val p = prefs(context)
        return FxSettings(
            enabled = p.getBoolean("enabled", false),
            bands = p.getString("bands", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() },
            bass = p.getInt("bass", 0),
            virtualizer = p.getInt("virtualizer", 0),
            preset = p.getString("preset", "Flat") ?: "Flat"
        )
    }

    fun attach(context: Context, sessionId: Int) {
        appContext = context.applicationContext
        release()
        settings = loadPrefs(context)
        if (sessionId == 0) return
        try {
            val e = Equalizer(0, sessionId)
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            info = FxInfo(
                bandCount = n,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                centerFreqsHz = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            )
            eq = e
        } catch (e: Exception) {
            eq = null
        }
        try {
            bassBoost = BassBoost(0, sessionId)
        } catch (e: Exception) {
            bassBoost = null
        }
        try {
            virt = Virtualizer(0, sessionId)
        } catch (e: Exception) {
            virt = null
        }
        applyAll()
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virt?.release() }
        eq = null
        bassBoost = null
        virt = null
    }

    private fun applyAll() {
        val s = settings
        eq?.let { e ->
            runCatching {
                e.enabled = s.enabled
                val n = e.numberOfBands.toInt()
                for (i in 0 until n) {
                    e.setBandLevel(i.toShort(), (s.bands.getOrNull(i) ?: 0).toShort())
                }
            }
        }
        bassBoost?.let { b ->
            runCatching {
                if (b.strengthSupported) b.setStrength(s.bass.toShort())
                b.enabled = s.enabled && s.bass > 0
            }
        }
        virt?.let { v ->
            runCatching {
                if (v.strengthSupported) v.setStrength(s.virtualizer.toShort())
                v.enabled = s.enabled && s.virtualizer > 0
            }
        }
    }

    /** Applies the change to the live effects; call [persist] once the user is done dragging. */
    fun update(newSettings: FxSettings) {
        settings = newSettings
        applyAll()
    }

    fun persist() {
        val ctx = appContext ?: return
        val s = settings
        prefs(ctx).edit()
            .putBoolean("enabled", s.enabled)
            .putString("bands", s.bands.joinToString(","))
            .putInt("bass", s.bass)
            .putInt("virtualizer", s.virtualizer)
            .putString("preset", s.preset)
            .apply()
    }

    fun presetLevels(name: String, info: FxInfo): List<Int> {
        val p = presets[name] ?: return List(info.bandCount) { 0 }
        val n = info.bandCount
        return (0 until n).map { i ->
            val pos = if (n == 1) 0f else i * 4f / (n - 1)
            val lo = floor(pos).toInt()
            val hi = min(lo + 1, 4)
            val v = p[lo] + (p[hi] - p[lo]) * (pos - lo)
            (v * (if (v >= 0) info.maxLevel else -info.minLevel)).toInt()
        }
    }
}

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val s = AudioFx.settings
    val info = AudioFx.info

    SubScreen(title = "Equalizer", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "Enable equalizer",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(checked = s.enabled, onCheckedChange = {
                    AudioFx.update(s.copy(enabled = it))
                    AudioFx.persist()
                })
            }
            if (info == null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "The equalizer isn't available yet. Start playing a song and come back.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            val bands = if (s.bands.size == info.bandCount) s.bands else List(info.bandCount) { 0 }

            Spacer(Modifier.height(12.dp))
            Text("Presets", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioFx.presets.keys.forEach { name ->
                    FilterChip(
                        selected = s.preset == name,
                        onClick = {
                            AudioFx.update(s.copy(enabled = true, bands = AudioFx.presetLevels(name, info), preset = name))
                            AudioFx.persist()
                        },
                        label = { Text(name) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Bands", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            bands.forEachIndexed { i, level ->
                val hz = info.centerFreqsHz.getOrNull(i) ?: 0
                val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, Modifier.padding(end = 8.dp).weight(0.25f), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = level.toFloat(),
                        onValueChange = {
                            val updated = bands.toMutableList().also { list -> list[i] = it.toInt() }
                            AudioFx.update(s.copy(enabled = true, bands = updated, preset = "Custom"))
                        },
                        onValueChangeFinished = { AudioFx.persist() },
                        valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                        modifier = Modifier.weight(0.6f)
                    )
                    Text(
                        "%+.1f dB".format(level / 100f),
                        Modifier.weight(0.25f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Effects", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Bass boost", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = s.bass.toFloat(),
                onValueChange = { AudioFx.update(s.copy(enabled = true, bass = it.toInt())) },
                onValueChangeFinished = { AudioFx.persist() },
                valueRange = 0f..1000f
            )
            Text("Surround (virtualizer)", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = s.virtualizer.toFloat(),
                onValueChange = { AudioFx.update(s.copy(enabled = true, virtualizer = it.toInt())) },
                onValueChangeFinished = { AudioFx.persist() },
                valueRange = 0f..1000f
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
