package com.homerentmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class HrmWindowWidth { Compact, Medium, Expanded }

fun windowWidthFor(screenWidthDp: Int): HrmWindowWidth = when {
    screenWidthDp < 600 -> HrmWindowWidth.Compact
    screenWidthDp < 840 -> HrmWindowWidth.Medium
    else -> HrmWindowWidth.Expanded
}

@Composable
fun rememberHrmWindowWidth(): HrmWindowWidth =
    windowWidthFor(LocalConfiguration.current.screenWidthDp)

fun hrmHorizontalPadding(width: HrmWindowWidth): Dp = when (width) {
    HrmWindowWidth.Compact -> 16.dp
    HrmWindowWidth.Medium -> 24.dp
    HrmWindowWidth.Expanded -> 32.dp
}

@Composable
fun hrmPagePadding(): PaddingValues {
    val horizontal = hrmHorizontalPadding(rememberHrmWindowWidth())
    return PaddingValues(start = horizontal, end = horizontal, top = 16.dp, bottom = 16.dp)
}

data class StatSpec(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val icon: ImageVector? = null,
    val accent: Color? = null,
    val onClick: (() -> Unit)? = null,
)

/** Cards reflow into 1, 2, 3 or 4 columns based on the available width. */
@Composable
fun StatGrid(
    specs: List<StatSpec>,
    modifier: Modifier = Modifier,
    minCardWidth: Dp = 148.dp,
) {
    if (specs.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spacing = 10.dp
        val columns = ((maxWidth + spacing) / (minCardWidth + spacing))
            .toInt()
            .coerceIn(1, specs.size)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            specs.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    rowItems.forEach { spec ->
                        StatCard(
                            label = spec.label,
                            value = spec.value,
                            supporting = spec.supporting,
                            icon = spec.icon,
                            accent = spec.accent ?: MaterialTheme.colorScheme.primary,
                            onClick = spec.onClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

data class QuickActionSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun QuickActionGrid(
    actions: List<QuickActionSpec>,
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 92.dp,
) {
    if (actions.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spacing = 10.dp
        val columns = ((maxWidth + spacing) / (minCellWidth + spacing))
            .toInt()
            .coerceIn(1, actions.size)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            actions.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    rowItems.forEach { action ->
                        OutlinedCard(
                            onClick = action.onClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.material3.Icon(
                                    action.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    action.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Responsive form dialog: full-width on phones, capped on tablets, always scrollable and
 * inset-aware so keyboard and system bars never hide the primary action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HrmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    confirmEnabled: Boolean = true,
    busy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            BoxWithConstraints {
                val bodyMaxHeight = if (maxHeight == Dp.Infinity) {
                    520.dp
                } else {
                    (maxHeight - 172.dp).coerceIn(120.dp, 560.dp)
                }
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = bodyMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = onDismiss, enabled = !busy) { Text(dismissLabel) }
                        Button(onClick = onConfirm, enabled = confirmEnabled && !busy) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    HrmDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
