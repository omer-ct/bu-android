@file:OptIn(ExperimentalMaterial3Api::class)

package com.codefixr.beummati.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.ShareColorMood
import com.codefixr.beummati.data.ShareTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stand-in content so the Settings previews show every template with the same words. */
val SAMPLE_SHARE_CARD = ShareCard(
    title = "Ali 'Imran",
    kind = "Qur’an",
    reference = "3:103",
    arabic = "وَٱعْتَصِمُوا۟ بِحَبْلِ ٱللَّهِ جَمِيعًۭا وَلَا تَفَرَّقُوا۟",
    transliteration = "waʿtaṣimū bi-ḥabli llāhi jamīʿan wa lā tafarraqū",
    english = "And hold firmly to the rope of Allah all together and do not become divided.",
    urdu = "اور اللہ کی رسی کو مضبوطی سے تھام لو، سب مل کر، اور تفرقہ نہ ڈالو"
)

private const val PREVIEW_WIDTH_PX = 320

/**
 * Renders [template] off the main thread and shows it once it's ready. Previews are small, so the
 * blank frame before the first emission is over in a frame or two.
 */
@Composable
fun ShareTemplatePreview(
    template: ShareTemplate,
    card: ShareCard = SAMPLE_SHARE_CARD,
    mood: ShareColorMood = ShareColorMood.DEFAULT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, template, card, mood) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                renderShareCard(card, template, PREVIEW_WIDTH_PX, context = context, mood = mood).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = template.label,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
        }
    }
}

/** Preview tile plus its name, highlighted when it's the chosen template. */
@Composable
fun ShareTemplateTile(
    template: ShareTemplate,
    selected: Boolean,
    card: ShareCard = SAMPLE_SHARE_CARD,
    mood: ShareColorMood = ShareColorMood.DEFAULT,
    onClick: () -> Unit
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(
        Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        ShareTemplatePreview(template, card, mood, Modifier.fillMaxWidth())
        Text(
            template.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp)
        )
        MutedText(template.blurb, maxLines = 2)
    }
}

/**
 * Full share studio: pick languages, edit/crop text, choose a design, then share image or text.
 * Replaces the old design-only sheet so Quran / Hadith / Duas all get the same controls.
 */
@Composable
fun ShareTemplateSheet(card: ShareCard, onDismiss: () -> Unit) {
    ShareStudioSheet(card = card, onDismiss = onDismiss)
}

@Composable
fun ShareStudioSheet(card: ShareCard, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentTemplate by SettingsStore.shareTemplate.collectAsState()
    val currentMood by SettingsStore.shareColorMood.collectAsState()
    val prefsAr by SettingsStore.shareArabic.collectAsState()
    val prefsEn by SettingsStore.shareEnglish.collectAsState()
    val prefsUr by SettingsStore.shareUrdu.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hasAr = card.arabic.isNotBlank()
    val hasEn = card.english.isNotBlank()
    val hasUr = card.urdu.isNotBlank()

    var includeAr by remember(card) { mutableStateOf(prefsAr && hasAr) }
    var includeEn by remember(card) { mutableStateOf(prefsEn && hasEn) }
    var includeUr by remember(card) { mutableStateOf(prefsUr && hasUr) }
    var editAr by remember(card) { mutableStateOf(card.arabic) }
    var editEn by remember(card) { mutableStateOf(card.english) }
    var editUr by remember(card) { mutableStateOf(card.urdu) }
    var template by remember(card) { mutableStateOf(currentTemplate) }
    var mood by remember(card) { mutableStateOf(currentMood) }
    var showEditors by remember { mutableStateOf(true) }

    LaunchedEffect(card) {
        if (!includeAr && !includeEn && !includeUr) {
            includeAr = hasAr
            includeEn = hasEn
            includeUr = hasUr
        }
    }

    val draft = remember(card, includeAr, includeEn, includeUr, editAr, editEn, editUr) {
        card.copy(
            arabic = if (includeAr && hasAr) editAr.trim() else "",
            english = if (includeEn && hasEn) editEn.trim() else "",
            urdu = if (includeUr && hasUr) editUr.trim() else ""
        )
    }

    fun applyLangPrefs() {
        SettingsStore.setShareArabic(includeAr)
        SettingsStore.setShareEnglish(includeEn)
        SettingsStore.setShareUrdu(includeUr)
    }

    fun persistLangChoices() {
        applyLangPrefs()
        SettingsStore.setShareTemplate(template)
        SettingsStore.setShareColorMood(mood)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            MutedText(
                "Choose languages, edit the text, then share as image or plain text.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            SectionLabel("Include languages")
            LangSwitchRow("Arabic", includeAr, hasAr) { includeAr = it }
            LangSwitchRow("English", includeEn, hasEn) { includeEn = it }
            LangSwitchRow("Urdu", includeUr, hasUr) { includeUr = it }

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = includeAr && includeEn && includeUr,
                    onClick = {
                        includeAr = hasAr
                        includeEn = hasEn
                        includeUr = hasUr
                    },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = includeAr && !includeEn && !includeUr,
                    onClick = {
                        includeAr = hasAr
                        includeEn = false
                        includeUr = false
                    },
                    label = { Text("Arabic only") }
                )
                FilterChip(
                    selected = !includeAr && includeEn && includeUr,
                    onClick = {
                        includeAr = false
                        includeEn = hasEn
                        includeUr = hasUr
                    },
                    label = { Text("EN + UR") }
                )
            }

            SectionLabel("Edit text")
            TextButton(
                onClick = { showEditors = !showEditors },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(if (showEditors) "Hide editors" else "Show editors / crop")
            }
            if (showEditors) {
                if (hasAr) {
                    OutlinedTextField(
                        value = editAr,
                        onValueChange = { editAr = it },
                        label = { Text("Arabic") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .heightIn(min = 88.dp),
                        enabled = includeAr,
                        minLines = 2,
                        maxLines = 6
                    )
                }
                if (hasEn) {
                    OutlinedTextField(
                        value = editEn,
                        onValueChange = { editEn = it },
                        label = { Text("English") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .heightIn(min = 72.dp),
                        enabled = includeEn,
                        minLines = 2,
                        maxLines = 5
                    )
                }
                if (hasUr) {
                    OutlinedTextField(
                        value = editUr,
                        onValueChange = { editUr = it },
                        label = { Text("Urdu") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .heightIn(min = 72.dp),
                        enabled = includeUr,
                        minLines = 2,
                        maxLines = 5
                    )
                }
                TextButton(
                    onClick = {
                        editAr = card.arabic
                        editEn = card.english
                        editUr = card.urdu
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Reset text to original")
                }
            }

            SectionLabel("Design")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ShareTemplate.entries, key = { it.name }) { t ->
                    ShareTemplateTile(
                        template = t,
                        selected = t == template,
                        card = draft,
                        mood = mood,
                        onClick = { template = t }
                    )
                }
            }
            Text(
                template.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            SectionLabel("Colour")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ShareColorMood.entries, key = { it.name }) { m ->
                    ColourMoodChip(
                        mood = m,
                        selected = m == mood,
                        onClick = { mood = m }
                    )
                }
            }
            Text(
                if (mood == ShareColorMood.DEFAULT) "Original design colours"
                else "${mood.label} grade on ${template.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    applyLangPrefs()
                    SettingsStore.setShareTemplate(template)
                    SettingsStore.setShareColorMood(mood)
                    onDismiss()
                    shareCardImage(context, draft, template, mood)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    if (mood == ShareColorMood.DEFAULT) "Share image · ${template.label}"
                    else "Share image · ${template.label} · ${mood.label}"
                )
            }
            OutlinedButton(
                onClick = {
                    applyLangPrefs()
                    onDismiss()
                    shareCard(context, draft)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Share text only")
            }
            TextButton(
                onClick = { persistLangChoices() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text("Save language + design + colour as default")
            }
        }
    }
}

@Composable
private fun ColourMoodChip(mood: ShareColorMood, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(mood.swatch))
                .border(if (selected) 2.5.dp else 1.dp, border, CircleShape)
        )
        Text(
            mood.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun LangSwitchRow(label: String, checked: Boolean, available: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (available) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Switch(
            checked = checked && available,
            onCheckedChange = onChange,
            enabled = available
        )
    }
}
