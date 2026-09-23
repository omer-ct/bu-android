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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.ShareTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stand-in content so the Settings previews show every template with the same words. */
val SAMPLE_SHARE_CARD = ShareCard(
    title = "Hold fast to the rope",
    kind = "Reminder",
    reference = "Āl ʿImrān 3:103",
    arabic = "وَٱعْتَصِمُوا۟ بِحَبْلِ ٱللَّهِ جَمِيعًۭا",
    transliteration = "waʿtaṣimū bi-ḥabli llāhi jamīʿan",
    english = "And hold firmly to the rope of Allah, all together, and do not become divided.",
    urdu = "اور اللہ کی رسی کو مضبوطی سے تھام لو، سب مل کر"
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
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, template, card) {
        value = withContext(Dispatchers.Default) {
            runCatching { renderShareCard(card, template, PREVIEW_WIDTH_PX).asImageBitmap() }.getOrNull()
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
        ShareTemplatePreview(template, card, Modifier.fillMaxWidth())
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
 * Template picker shown from the share menu. Tapping a design remembers it as the default and
 * hands the rendered image straight to the system share sheet.
 */
@Composable
fun ShareTemplateSheet(card: ShareCard, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current by SettingsStore.shareTemplate.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pick a design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    MutedText("Your choice becomes the default for next time")
                }
                TextButton(onClick = {
                    onDismiss()
                    shareCard(context, card)
                }) {
                    Text("Text only")
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ShareTemplate.entries, key = { it.name }) { template ->
                    ShareTemplateTile(
                        template = template,
                        selected = template == current,
                        card = card,
                        onClick = {
                            SettingsStore.setShareTemplate(template)
                            onDismiss()
                            shareCardImage(context, card, template)
                        }
                    )
                }
            }
        }
    }
}
