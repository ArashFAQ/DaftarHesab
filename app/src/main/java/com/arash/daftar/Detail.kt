@file:OptIn(ExperimentalMaterial3Api::class)

package com.arash.daftar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** پیام «حذف شد» با دکمه‌ی برگرداندن */
fun CoroutineScope.undoSnackbar(snackbar: SnackbarHostState, message: String, undo: suspend () -> Unit) {
    launch {
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(message, actionLabel = "برگردان", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) undo()
    }
}

private sealed class DetailDialog {
    data object NewDebt : DetailDialog()
    data object Payment : DetailDialog()
    data object Settle : DetailDialog()
    data object Rename : DetailDialog()
    data object Note : DetailDialog()
    data object Delete : DetailDialog()
    data class Edit(val tx: Tx) : DetailDialog()
}

@Composable
fun DetailScreen(
    repo: Repo,
    customerId: String,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val customer by repo.dao.customerFlow(customerId).collectAsState(initial = null)
    val txs by repo.dao.txsOf(customerId).collectAsState(initial = emptyList())
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }
    var menu by remember { mutableStateOf(false) }

    val debts = txs.filter { it.kind == Kind.DEBT }.sumOf { it.amount }
    val paid = txs.filter { it.kind == Kind.PAYMENT }.sumOf { it.amount }
    val balance = debts - paid
    val c = customer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "بیشتر") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("ویرایش اسم") }, onClick = { menu = false; dialog = DetailDialog.Rename })
                        DropdownMenuItem(text = { Text("توضیحات مشتری") }, onClick = { menu = false; dialog = DetailDialog.Note })
                        DropdownMenuItem(
                            text = { Text("حذف مشتری", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; dialog = DetailDialog.Delete }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مانده", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            BalanceText(balance, big = true)
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("جمع بدهی‌ها", style = MaterialTheme.typography.labelMedium)
                                Text(Fa.toman(debts), color = debtColor(), fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("جمع پرداخت‌ها", style = MaterialTheme.typography.labelMedium)
                                Text(Fa.toman(paid), color = payColor(), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!c?.note.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                c!!.note,
                                Modifier.fillMaxWidth().clickable { dialog = DetailDialog.Note },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialog = DetailDialog.NewDebt }, Modifier.weight(1f)) { Text("بدهی جدید") }
                    Button(onClick = { dialog = DetailDialog.Payment }, Modifier.weight(1f)) { Text("پرداخت") }
                    if (balance > 0) {
                        FilledTonalButton(onClick = { dialog = DetailDialog.Settle }, Modifier.weight(1f)) { Text("تسویه کامل") }
                    }
                }
            }
            item {
                Text(
                    "تاریخچه",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (txs.isEmpty()) {
                item { Text("ردیفی ثبت نشده.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(txs, key = { it.id }) { t ->
                TxRow(t) { dialog = DetailDialog.Edit(t) }
            }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> {}
        DetailDialog.NewDebt -> TxDialog(
            title = "بدهی جدید برای ${c?.name ?: ""}",
            initialAmount = null, initialNote = "", initialDate = System.currentTimeMillis(),
            onSave = { a, n, dt -> scope.launch { repo.addTx(customerId, Kind.DEBT, a, n, dt) }; close() },
            onDismiss = close
        )
        DetailDialog.Payment -> TxDialog(
            title = "ثبت پرداخت",
            hint = if (balance > 0) "مانده‌ی فعلی: " + Fa.toman(balance) else null,
            initialAmount = null, initialNote = "", initialDate = System.currentTimeMillis(),
            onSave = { a, n, dt -> scope.launch { repo.addTx(customerId, Kind.PAYMENT, a, n, dt) }; close() },
            onDismiss = close
        )
        DetailDialog.Settle -> AlertDialog(
            onDismissRequest = close,
            title = { Text("تسویه کامل") },
            text = { Text("پرداخت " + Fa.toman(balance) + " ثبت شود و حساب ${c?.name ?: ""} صفر شود؟") },
            confirmButton = {
                Button(onClick = {
                    val amount = balance
                    scope.launch { repo.addTx(customerId, Kind.PAYMENT, amount, "تسویه کامل", System.currentTimeMillis()) }
                    close()
                }) { Text("تسویه") }
            },
            dismissButton = { TextButton(onClick = close) { Text("انصراف") } }
        )
        is DetailDialog.Edit -> TxDialog(
            title = if (d.tx.kind == Kind.DEBT) "ویرایش بدهی" else "ویرایش پرداخت",
            initialAmount = d.tx.amount, initialNote = d.tx.note, initialDate = d.tx.date,
            confirmText = "ذخیره",
            onSave = { a, n, dt -> scope.launch { repo.editTx(d.tx, a, n, dt) }; close() },
            onDelete = {
                val id = d.tx.id
                scope.launch { repo.setTxDeleted(id, true) }
                close()
                scope.undoSnackbar(snackbar, "ردیف حذف شد") { repo.setTxDeleted(id, false) }
            },
            onDismiss = close
        )
        DetailDialog.Rename -> TextDialog(
            title = "ویرایش اسم", label = "اسم مشتری", initial = c?.name ?: "", singleLine = true,
            onSave = { name ->
                scope.launch {
                    if (repo.renameCustomer(customerId, name)) close()
                    else snackbar.showSnackbar("این اسم خالی است یا مال مشتری دیگری است")
                }
            },
            onDismiss = close
        )
        DetailDialog.Note -> TextDialog(
            title = "توضیحات مشتری", label = "مثلا شماره تلفن یا آدرس", initial = c?.note ?: "", singleLine = false,
            onSave = { n -> scope.launch { repo.setCustomerNote(customerId, n) }; close() },
            onDismiss = close
        )
        DetailDialog.Delete -> AlertDialog(
            onDismissRequest = close,
            title = { Text("حذف مشتری") },
            text = { Text("${c?.name ?: ""} با همه‌ی بدهی‌ها و پرداخت‌هایش از لیست حذف شود؟") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { repo.setCustomerDeleted(customerId, true) }
                    close()
                    scope.undoSnackbar(snackbar, "${c?.name ?: "مشتری"} حذف شد") { repo.setCustomerDeleted(customerId, false) }
                    onBack()
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = close) { Text("انصراف") } }
        )
    }
}

@Composable
private fun TxRow(t: Tx, onClick: () -> Unit) {
    val isDebt = t.kind == Kind.DEBT
    val color = if (isDebt) debtColor() else payColor()
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isDebt) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                null, tint = color, modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isDebt) "بدهی" else "پرداخت", fontWeight = FontWeight.SemiBold)
                Text(
                    Jalali.date(t.date) + "  ساعت " + Jalali.time(t.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (t.note.isNotBlank()) {
                    Text(t.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(Fa.toman(t.amount), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TextDialog(
    title: String,
    label: String,
    initial: String,
    singleLine: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
