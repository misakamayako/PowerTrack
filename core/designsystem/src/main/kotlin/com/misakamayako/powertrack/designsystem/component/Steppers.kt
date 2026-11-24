package com.misakamayako.powertrack.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.designsystem.theme.PtType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 重量步进器（§6）：
 * - 左右滑动 ±step（默认 2.5kg）
 * - 左右按钮点按加减
 * - 点数值唤起**自定义数字键盘**（不用系统键盘）
 */
@Composable
fun WeightStepper(
    valueKg: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 2.5,
    label: String = "kg",
    enabled: Boolean = true,
) {
    var showKeypad by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val rounded = remember(valueKg) { (valueKg * 2).roundToInt() / 2.0 }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm),
    ) {
        StepButton(text = "−", enabled = enabled) {
            onValueChange((rounded - step).coerceAtLeast(0.0))
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 72.dp)
                .pointerInput(enabled, rounded) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                    ) { _, dragAmount ->
                        dragAccumulator += dragAmount
                        while (abs(dragAccumulator) >= 28f) {
                            val direction = if (dragAccumulator > 0) 1 else -1
                            dragAccumulator -= direction * 28f
                            onValueChange((rounded + direction * step).coerceAtLeast(0.0))
                        }
                    }
                }
                .semantics { contentDescription = "重量 ${formatNumber(rounded)} $label" }
                .clickable(enabled = enabled) { showKeypad = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text(
                    text = "${formatNumber(rounded)} $label",
                    fontFamily = FontFamily.Monospace,
                    fontSize = PtType.numeric,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // 窄容器里（弹窗、并排控件）绝不折行：折行会挤在固定 72dp 高度里叠字
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        StepButton(text = "+", enabled = enabled) {
            onValueChange(rounded + step)
        }
    }

    if (showKeypad) {
        NumberPadDialog(
            title = "输入重量",
            initial = formatNumber(rounded),
            unit = label,
            allowDecimal = true,
            onDismiss = { showKeypad = false },
            onConfirm = { text ->
                text.toDoubleOrNull()?.let { onValueChange(it.coerceAtLeast(0.0)) }
                showKeypad = false
            },
        )
    }
}

/** 次数 / 秒数步进器。 */
@Composable
fun CountStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "次",
    min: Int = 0,
    max: Int = 999,
) {
    var showKeypad by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm),
    ) {
        StepButton(text = "−") { onValueChange((value - 1).coerceAtLeast(min)) }
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 72.dp).clickable { showKeypad = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text(
                    text = "$value $label",
                    fontFamily = FontFamily.Monospace,
                    fontSize = PtType.numeric,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        StepButton(text = "+") { onValueChange((value + 1).coerceAtMost(max)) }
    }

    if (showKeypad) {
        NumberPadDialog(
            title = "输入$label",
            initial = value.toString(),
            unit = label,
            allowDecimal = false,
            onDismiss = { showKeypad = false },
            onConfirm = { text ->
                text.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                showKeypad = false
            },
        )
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(64.dp).height(64.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

/** 自定义数字键盘弹窗（§6：不要系统键盘）。 */
@Composable
fun NumberPadDialog(
    title: String,
    initial: String,
    unit: String,
    allowDecimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var buffer by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                Text(
                    text = if (buffer.isEmpty()) "0" else "$buffer $unit",
                    fontFamily = FontFamily.Monospace,
                    fontSize = PtType.numericLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (allowDecimal) "." else "清空", "0", "⌫"),
                )
                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm),
                    ) {
                        row.forEach { key ->
                            OutlinedButton(
                                onClick = {
                                    buffer = when (key) {
                                        "⌫" -> buffer.dropLast(1)
                                        "清空" -> ""
                                        "." -> if (buffer.contains('.')) buffer else (buffer.ifEmpty { "0" } + ".")
                                        else -> buffer + key
                                    }
                                },
                                modifier = Modifier.weight(1f).height(64.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(key, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(buffer) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** `100.0 -> "100"`，`102.5 -> "102.5"`，`62.25 -> "62.25"`。 */
fun formatNumber(value: Double): String {
    if (abs(value - value.roundToInt()) < 0.0001) return value.roundToInt().toString()
    val scaled = (value * 100).roundToInt()
    val intPart = scaled / 100
    val frac = abs(scaled % 100)
    return when {
        frac % 10 == 0 -> "$intPart.${frac / 10}"
        else -> "$intPart.${frac.toString().padStart(2, '0')}"
    }
}
