package com.example.jetnews.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED black")
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val minDurationSec: Int = 0,
    val hiddenFolders: Set<String> = emptySet(),
    val defaultSort: SortOrder = SortOrder.TITLE,
    val resumeOnLaunch: Boolean = true,
    val lowPower: Boolean = false,
    val reduceAnimations: Boolean = false
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null).orEmpty()) }.getOrDefault(d.theme),
            dynamicColor = prefs.getBoolean("dynamic_color", d.dynamicColor),
            minDurationSec = prefs.getInt("min_duration", d.minDurationSec),
            hiddenFolders = prefs.getStringSet("hidden_folders", emptySet())?.toSet() ?: emptySet(),
            defaultSort = runCatching { SortOrder.valueOf(prefs.getString("sort", null).orEmpty()) }
                .getOrDefault(d.defaultSort),
            resumeOnLaunch = prefs.getBoolean("resume", d.resumeOnLaunch),
            lowPower = prefs.getBoolean("low_power", d.lowPower),
            reduceAnimations = prefs.getBoolean("reduce_animations", d.reduceAnimations)
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic_color", s.dynamicColor)
            .putInt("min_duration", s.minDurationSec)
            .putStringSet("hidden_folders", s.hiddenFolders)
            .putString("sort", s.defaultSort.name)
            .putBoolean("resume", s.resumeOnLaunch)
            .putBoolean("low_power", s.lowPower)
            .putBoolean("reduce_animations", s.reduceAnimations)
            .apply()
    }
}

fun buildColorScheme(ctx: Context, settings: AppSettings, dark: Boolean): ColorScheme {
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(ctx)
        dynamic -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    return if (dark && settings.theme == ThemeMode.AMOLED) {
        base.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color.Black)
    } else {
        base
    }
}
