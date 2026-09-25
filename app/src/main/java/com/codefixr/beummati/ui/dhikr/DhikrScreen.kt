package com.codefixr.beummati.ui.dhikr

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefixr.beummati.ui.ArabicScriptText
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.ScreenScaffold

private val PHRASES = listOf(
    "سبحان الله",
    "الحمد لله",
    "الله أكبر",
    "لا إله إلا الله"
)

private const val PREFS = "beummati_dhikr"
private const val KEY_COUNT = "beummati.dhikr.count"
private const val KEY_TARGET = "beummati.dhikr.target"
private const val DEFAULT_TARGET = 33

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DhikrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    var phrase by remember { mutableStateOf(PHRASES.first()) }
    var count by remember { mutableIntStateOf(prefs.getInt(KEY_COUNT, 0)) }
    var target by remember {
        mutableIntStateOf(
            prefs.getInt(KEY_TARGET, DEFAULT_TARGET).coerceIn(11, 100).let { t ->
                if (t % 11 == 0) t else DEFAULT_TARGET
            }
        )
    }

    fun saveCount(value: Int) {
        count = value
        prefs.edit().putInt(KEY_COUNT, value).apply()
    }

    fun saveTarget(value: Int) {
        target = value
        prefs.edit().putInt(KEY_TARGET, value).apply()
    }

    val safeTarget = target.coerceAtLeast(1)
    val progress = (count % safeTarget).toFloat() / safeTarget.toFloat()

    ScreenScaffold(title = "Dhikr", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ContentCard {
                Text("Phrase", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PHRASES.forEach { p ->
                        FilterChip(
                            selected = phrase == p,
                            onClick = { phrase = p },
                            label = { Text(p) }
                        )
                    }
                }
            }

            ContentCard {
                ArabicScriptText(
                    phrase,
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
                Text(
                    "$count",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    "of $target",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp)
                )
            }

            Button(
                onClick = { saveCount(count + 1) },
                shape = CircleShape,
                modifier = Modifier.size(140.dp)
            ) {
                Text("Tap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { saveCount(0) }) { Text("Reset") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { saveTarget((target - 11).coerceAtLeast(11)) },
                        enabled = target > 11
                    ) { Text("−") }
                    Text("Target $target", fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = { saveTarget((target + 11).coerceAtMost(100)) },
                        enabled = target < 100
                    ) { Text("+") }
                }
            }
        }
    }
}
