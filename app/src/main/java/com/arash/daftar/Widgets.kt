@file:OptIn(ExperimentalLayoutApi::class)

package com.arash.daftar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ================================================================ مبلغ

/** digits فقط رقم انگلیسی است؛ نمایش با جداکننده و رقم فارسی */
@Composable
fun AmountField(
    digits: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "مبلغ",
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {}
) {
    val shown = if (digits.isEmpty()) "" else Fa.num(digits.toLong())
    OutlinedTextField(
        value = TextFieldValue(shown, TextRange(shown.length)),
        onValueChange = { onChange(Fa.digits(it.text).trimStart('0').take(13)) },
        label = { Text(label) },
        suffix = { Text("تومان") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = modifier.fillMaxWidth()
    )
}

// ================================================================ تاریخ

@Composable
fun DateRow(date: Long, onChange: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { picking = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(
            (if (Jalali.isToday(date)) "امروز، " else "") + Jalali.longDate(date),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Text("تغییر تاریخ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (picking) {
        JalaliDateDialog(
            initial = Jalali.fromMillis(date),
            onPick = { j ->
                // امروز: ساعت فعلی؛ روزهای دیگر: ساعت ۱۲ ظهر
                onChange(
                    if (Jalali.isToday(Jalali.toMillis(j[0], j[1], j[2], 12, 0))) System.currentTimeMillis()
                    else Jalali.toMillis(j[0], j[1], j[2], 12, 0)
                )
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
fun JalaliDateDialog(
    initial: IntArray,
    onPick: (IntArray) -> Unit,
    onDismiss: () -> Unit
) {
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val todayKey = today[0] * 10000 + today[1] * 100 + today[2]
    var y by remember { mutableIntStateOf(initial[0]) }
    var m by remember { mutableIntStateOf(initial[1]) }
    var d by remember { mutableIntStateOf(initial[2]) }
    val len = Jalali.monthLength(y, m)
    if (d > len) d = len
    fun key(yy: Int, mm: Int, dd: Int) = yy * 10000 + mm * 100 + dd

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { y = today[0]; m = today[1]; d = today[2] }) { Text("امروز") }
                    OutlinedButton(onClick = {
                        val j = Jalali.fromMillis(System.currentTimeMillis() - 86_400_000L)
                        y = j[0]; m = j[1]; d = j[2]
                    }) { Text("دیروز") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { y-- }) { Icon(Icons.Filled.KeyboardArrowRight, "سال قبل") }
                    Text(
                        Fa.toFa(y.toString()), Modifier.weight(1f),
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { y++ }, enabled = y < today[0]) { Icon(Icons.Filled.KeyboardArrowLeft, "سال بعد") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..12).forEach { mm ->
                        val future = key(y, mm, 1) > todayKey
                        FilterChip(
                            selected = m == mm, enabled = !future,
                            onClick = { m = mm },
                            label = { Text(Jalali.monthName(mm)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..len).forEach { dd ->
                        val future = key(y, m, dd) > todayKey
                        val sel = d == dd
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable(enabled = !future) { d = dd }
                                .alpha(if (future) 0.3f else 1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Fa.toFa(dd.toString()),
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = key(y, m, d) <= todayKey,
                onClick = { onPick(intArrayOf(y, m, d)) }
            ) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ================================================================ پنجره‌ی ثبت/ویرایش بدهی یا پرداخت

@Composable
fun TxDialog(
    title: String,
    initialAmount: Long?,
    initialNote: String,
    initialDate: Long,
    hint: String? = null,
    confirmText: String = "ثبت",
    onSave: (amount: Long, note: String, date: Long) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount?.toString() ?: "") }
    var note by remember { mutableStateOf(initialNote) }
    var date by remember { mutableLongStateOf(initialDate) }
    val value = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                AmountField(amount, { amount = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("توضیحات (اختیاری)") },
                    minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                DateRow(date) { date = it }
                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDelete) {
                        Text("حذف این ردیف", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = value > 0, onClick = { onSave(value, note, date) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ================================================================ وضعیت بکاپ

@Composable
fun SyncBadge(repo: Repo, onClick: () -> Unit) {
    val st by Sync.status.collectAsState()
    val pending by repo.dao.pendingCount().collectAsState(initial = 0)
    IconButton(onClick = onClick) {
        when {
            st.running -> Icon(Icons.Filled.CloudSync, "در حال بکاپ", tint = MaterialTheme.colorScheme.primary)
            pending == 0 && st.error == null -> Icon(Icons.Filled.CloudDone, "همه‌چیز بکاپ شده", tint = payColor())
            pending > 0 -> BadgedBox(badge = { Badge { Text(Fa.toFa(pending.coerceAtMost(99).toString())) } }) {
                Icon(
                    if (st.error != null) Icons.Filled.CloudOff else Icons.Filled.CloudUpload,
                    "در انتظار بکاپ",
                    tint = if (st.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> Icon(Icons.Filled.CloudOff, "خطای بکاپ", tint = MaterialTheme.colorScheme.error)
        }
    }
}
