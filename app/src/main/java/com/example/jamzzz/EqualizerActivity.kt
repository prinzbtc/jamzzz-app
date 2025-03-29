package com.example.jamzzz

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.media3.exoplayer.ExoPlayer
import com.example.jamzzz.ui.theme.GradientBackground
import com.example.jamzzz.ui.theme.JamzzzTheme
import com.example.jamzzz.ui.theme.TextWhite
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EqualizerActivity : ComponentActivity() {
    private var equalizer: Equalizer? = null
    private lateinit var sharedPreferences: SharedPreferences
    
    companion object {
        private const val PREF_NAME = "jamzzz_equalizer_prefs"
        private const val KEY_PRESET = "selected_preset"
        private const val KEY_CUSTOM_BANDS = "custom_bands"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Get the audio session ID from the intent
        val audioSessionId = intent.getIntExtra("audioSessionId", 0)
        
        // Initialize equalizer if we have a valid session ID
        if (audioSessionId > 0) {
            try {
                equalizer = Equalizer(1000, audioSessionId).apply {
                    enabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Could not initialize equalizer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Get saved preset from SharedPreferences
        val savedPreset = sharedPreferences.getString(KEY_PRESET, "Normal")
        val savedCustomBands = sharedPreferences.getString(KEY_CUSTOM_BANDS, null)
        
        setContent {
            JamzzzTheme(darkTheme = true) {
                EqualizerScreen(
                    equalizer = equalizer,
                    initialPreset = savedPreset ?: "Normal",
                    initialCustomBands = savedCustomBands,
                    onPresetSelected = { preset, customBandsJson ->
                        // Save the selected preset to SharedPreferences
                        sharedPreferences.edit().apply {
                            putString(KEY_PRESET, preset)
                            if (preset == "Custom" && customBandsJson != null) {
                                putString(KEY_CUSTOM_BANDS, customBandsJson)
                            }
                            apply()
                        }
                    },
                    onBackPressed = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        // Release equalizer resources
        equalizer?.release()
        equalizer = null
        super.onDestroy()
    }
}

@Composable
fun EqualizerScreen(
    equalizer: Equalizer?, 
    initialPreset: String = "Normal",
    initialCustomBands: String? = null,
    onPresetSelected: (String, String?) -> Unit = { _, _ -> },
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Get the number of bands from the equalizer or use default
    val bandCount = remember { equalizer?.numberOfBands?.toInt() ?: 10 }
    
    // Get the center frequencies for each band or use defaults
    val frequencies = remember {
        if (equalizer != null) {
            List(bandCount) { band ->
                val freq = equalizer.getCenterFreq(band.toShort()) / 1000
                when {
                    freq < 1000 -> "${freq}Hz"
                    else -> "${freq/1000}kHz"
                }
            }
        } else {
            listOf("32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
        }
    }
    
    // Get the available presets from the equalizer or use defaults
    val presets = remember {
        if (equalizer != null) {
            val presetCount = equalizer.numberOfPresets
            List(presetCount.toInt()) { preset ->
                equalizer.getPresetName(preset.toShort())
            }.plus("Custom") // Add custom option
        } else {
            listOf("Normal", "Classical", "Dance", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock", "Custom")
        }
    }
    
    // Track the currently selected preset
    var selectedPreset by remember { mutableStateOf(initialPreset) }
    
    // Convert saved custom bands JSON back to list if available
    val initialBandValues = remember {
        if (initialCustomBands != null && initialPreset == "Custom") {
            try {
                Json.decodeFromString<List<Float>>(initialCustomBands)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    // Get the min/max levels for the equalizer bands
    val minLevel = remember { equalizer?.bandLevelRange?.get(0) ?: -1500 }
    val maxLevel = remember { equalizer?.bandLevelRange?.get(1) ?: 1500 }
    val levelRange = remember { maxLevel - minLevel }
    
    // Initialize slider values based on saved values, current equalizer settings, or defaults
    var sliderValues by remember { 
        mutableStateOf(
            when {
                // If we have custom bands saved and preset is Custom, use those
                initialBandValues != null -> {
                    if (initialBandValues.size == frequencies.size) {
                        initialBandValues
                    } else {
                        List(frequencies.size) { 0.5f }
                    }
                }
                // Otherwise use equalizer values if available
                equalizer != null -> {
                    List(bandCount) { band ->
                        val level = equalizer.getBandLevel(band.toShort())
                        (level - minLevel).toFloat() / levelRange
                    }
                }
                // Default fallback
                else -> {
                    List(frequencies.size) { 0.5f } // Default to middle position
                }
            }
        )
    }
    
    // Function to apply preset
    fun applyPreset(preset: String) {
        selectedPreset = preset
        
        // Notify the activity about the preset change
        if (preset == "Custom") {
            // Convert slider values to JSON for storage
            val customBandsJson = Json.encodeToString(sliderValues)
            onPresetSelected(preset, customBandsJson)
        } else {
            onPresetSelected(preset, null)
        }
        
        if (equalizer != null) {
            try {
                if (preset != "Custom") {
                    // Find the preset index
                    val presetIndex = presets.indexOf(preset).toShort()
                    if (presetIndex >= 0 && presetIndex < equalizer.numberOfPresets) {
                        // Apply the preset
                        equalizer.usePreset(presetIndex)
                        
                        // Update slider values to reflect the new preset
                        sliderValues = List(bandCount) { band ->
                            val level = equalizer.getBandLevel(band.toShort())
                            (level - minLevel).toFloat() / levelRange
                        }
                        
                        Toast.makeText(context, "Applied preset: $preset", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // For custom, we keep the current slider values
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error applying preset: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Fallback behavior when equalizer is not available
            sliderValues = when (preset) {
                "Classical" -> listOf(0.4f, 0.4f, 0.4f, 0.4f, 0.5f, 0.5f, 0.4f, 0.3f, 0.3f, 0.2f)
                "Dance" -> listOf(0.7f, 0.8f, 0.85f, 0.75f, 0.6f, 0.4f, 0.4f, 0.5f, 0.7f, 0.8f)
                "Folk" -> listOf(0.8f, 0.5f, 0.5f, 0.7f, 0.7f, 0.6f, 0.5f, 0.4f, 0.4f, 0.4f)
                "Heavy Metal" -> listOf(0.9f, 0.8f, 0.5f, 0.7f, 0.2f, 0.5f, 0.8f, 0.9f, 0.8f, 0.5f)
                "Hip Hop" -> listOf(0.9f, 0.95f, 0.8f, 0.6f, 0.4f, 0.4f, 0.5f, 0.7f, 0.8f, 0.8f)
                "Jazz" -> listOf(0.9f, 0.8f, 0.6f, 0.7f, 0.4f, 0.4f, 0.5f, 0.6f, 0.8f, 0.8f)
                "Pop" -> listOf(0.4f, 0.5f, 0.5f, 0.6f, 0.9f, 0.8f, 0.6f, 0.5f, 0.4f, 0.4f)
                "Rock" -> listOf(1.0f, 0.9f, 0.8f, 0.5f, 0.4f, 0.4f, 0.5f, 0.8f, 0.9f, 0.9f)
                else -> List(frequencies.size) { 0.5f } // Normal/flat
            }
        }
    }
    
    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text("Equalizer")
                    },
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colors.onPrimary,
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preset selector
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.h6,
                    color = TextWhite,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Dropdown menu for presets
                var expanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.3f),
                            contentColor = TextWhite
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedPreset)
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select Preset"
                            )
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colors.surface)
                    ) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                onClick = {
                                    applyPreset(preset)
                                    expanded = false
                                }
                            ) {
                                Text(
                                    text = preset,
                                    color = if (preset == selectedPreset) 
                                        MaterialTheme.colors.primary 
                                    else 
                                        TextWhite
                                )
                            }
                        }
                    }
                }
                
                // Equalizer sliders
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.h6,
                    color = TextWhite,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Main container for equalizer
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    // Draw 5 vertical white lines to represent the main bands
                    val mainBandPositions = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f) // 5 evenly spaced positions
                    
                    mainBandPositions.forEach { position ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.85f) // Leave space for labels
                                .width(1.dp)
                                .background(TextWhite.copy(alpha = 0.5f))
                                .align(Alignment.TopCenter)
                                .offset(x = ((position * LocalConfiguration.current.screenWidthDp) - (LocalConfiguration.current.screenWidthDp/2)).dp)
                        )
                    }
                    
                    // Frequency sliders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        frequencies.forEachIndexed { index, freq ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Custom vertical slider with draggable button-dot
                                Box(
                                    modifier = Modifier
                                        .height(200.dp)
                                        .width(30.dp)
                                ) {
                                    // Vertical track
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .align(Alignment.Center)
                                            .background(
                                                color = MaterialTheme.colors.surface,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    
                                    // Active track (below the thumb)
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight(sliderValues[index])
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                color = MaterialTheme.colors.primary,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    
                                    // Draggable thumb (button-dot)
                                    val thumbSize = 16.dp
                                    Box(
                                        modifier = Modifier
                                            .size(thumbSize)
                                            .offset(y = -((sliderValues[index] * 200).dp) + (200.dp - thumbSize))
                                            .align(Alignment.TopCenter)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .draggable(
                                                orientation = Orientation.Vertical,
                                                state = rememberDraggableState { delta ->
                                                    // Convert drag delta to value change (reversed because dragging up should increase value)
                                                    val newValue = sliderValues[index] - (delta / 200f)
                                                    val clampedValue = newValue.coerceIn(0f, 1f)
                                                    
                                                    if (clampedValue != sliderValues[index]) {
                                                        val newSliderValues = sliderValues.toMutableList()
                                                        newSliderValues[index] = clampedValue
                                                        sliderValues = newSliderValues
                                                        selectedPreset = "Custom" // When user adjusts, it becomes custom
                                                        
                                                        // Apply the band level change to the equalizer
                                                        equalizer?.let { eq ->
                                                            try {
                                                                // Convert normalized value (0-1) to actual level
                                                                val minEqLevel = eq.bandLevelRange[0]
                                                                val maxEqLevel = eq.bandLevelRange[1]
                                                                val eqLevelRange = maxEqLevel - minEqLevel
                                                                val actualLevel = (clampedValue * eqLevelRange + minEqLevel).toInt().toShort()
                                                                
                                                                // Set the band level
                                                                eq.setBandLevel(index.toShort(), actualLevel)
                                                            } catch (e: Exception) {
                                                                coroutineScope.launch {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Error adjusting equalizer: ${e.message}",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                    )
                                }
                                
                                // Frequency label
                                Text(
                                    text = freq,
                                    fontSize = 10.sp,
                                    color = TextWhite,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Reset button
                Button(
                    onClick = { applyPreset("Normal") },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    Text("Reset to Flat")
                }
                
                // Show status if equalizer is not available
                if (equalizer == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Note: Equalizer is in preview mode. Start playing music to enable full functionality.",
                        color = TextWhite.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
