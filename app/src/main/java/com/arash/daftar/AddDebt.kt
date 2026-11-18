@file:OptIn(ExperimentalMaterial3Api::class)

package com.arash.daftar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AddDebtScreen(repo: Repo, scope: CoroutineScope, onDone: () -> Unit) {
    val all by repo.dao.summaries().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nameFocused by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    val key = Fa.norm(name)
    val exact = all.firstOrNull { Fa.norm(it.name) == key }
    // پیشنهاد اسم‌های قبلی: اول آن‌هایی که با متن شروع می‌شوند
    val suggestions = if (key.isEmpty() || !nameFocused) emptyList() else all
        .filter { Fa.norm(it.name).contains(key) && Fa.norm(it.name) != key }
        .sortedBy { if (Fa.norm(it.name).startsWith(key)) 0 else 1 }
        .take(6)
    val value = amount.toLongOrNull() ?: 0L
    val canSave = Fa.cleanName(name).isNotEmpty() && value > 0 && !saving

    fun save() {
        if (!canSave) return
        saving = true
        val n = name
        val a = value
        val nt = note
        val d = date
        scope.launch {
            repo.addDebt(n, a, nt, d)
        }
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بدهی جدید", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { onDone() }) { Icon(Icons.Filled.ArrowBack, "بازگشت") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("اسم مشتری") },
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
                    .onFocusChanged { nameFocused = it.isFocused }
            )
            if (suggestions.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    suggestions.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                name = s.name
                                amountFocus.requestFocus()
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.name, Modifier.weight(1f))
                            if (s.balance != 0L) {
                                Text(
                                    Fa.toman(kotlin.math.abs(s.balance)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (s.balance > 0) debtColor() else payColor()
                                )
                            }
                        }
                    }
                }
            }
            if (key.isNotEmpty()) {
                Text(
                    if (exact != null) {
                        "به حساب «${exact.name}» اضافه می‌شود" +
                            (if (exact.balance > 0) " (بدهی فعلی: ${Fa.toman(exact.balance)})" else "")
                    } else "مشتری جدید ساخته می‌شود",
                    Modifier.padding(top = 6.dp, start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exact != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            AmountField(
                amount, { amount = it },
                modifier = Modifier.focusRequester(amountFocus)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("توضیحات (مثلا ۲ مانتو و یک شال)") },
                minLines = 3, maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            DateRow(date) { date = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { save() }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("ثبت بدهی") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
