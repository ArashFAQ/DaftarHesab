@file:OptIn(ExperimentalMaterial3Api::class)

package com.arash.daftar

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.Collator
import java.util.Locale

enum class SortBy(val title: String, val defaultDesc: Boolean) {
    AMOUNT("مبلغ", true),
    FIRST("اولین بدهی", false),
    LAST("آخرین بدهی", true),
    NAME("اسم", false)
}

private object SortPrefs {
    fun load(ctx: Context): Pair<SortBy, Boolean> {
        val p = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val s = runCatching { SortBy.valueOf(p.getString("sort", "AMOUNT")!!) }.getOrDefault(SortBy.AMOUNT)
        return s to p.getBoolean("desc", s.defaultDesc)
    }

    fun save(ctx: Context, s: SortBy, desc: Boolean) {
        ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
            .putString("sort", s.name).putBoolean("desc", desc).apply()
    }
}

private val collator: Collator = Collator.getInstance(Locale("fa"))

fun sortCustomers(list: List<CustomerSummary>, by: SortBy, desc: Boolean): List<CustomerSummary> {
    val cmp: Comparator<CustomerSummary> = when (by) {
        SortBy.AMOUNT -> compareBy { it.balance }
        SortBy.NAME -> Comparator { a, b -> collator.compare(a.name, b.name) }
        SortBy.FIRST -> compareBy { it.firstDebt ?: 0L }
        SortBy.LAST -> compareBy { it.lastDebt ?: 0L }
    }
    val sorted = list.sortedWith(if (desc) cmp.reversed() else cmp)
    // مشتری‌هایی که تاریخ بدهی ندارند، همیشه آخر لیست
    return if (by == SortBy.FIRST || by == SortBy.LAST) {
        val (has, none) = sorted.partition { (if (by == SortBy.FIRST) it.firstDebt else it.lastDebt) != null }
        has + none
    } else sorted
}

@Composable
fun HomeScreen(
    repo: Repo,
    snackbar: SnackbarHostState,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val all by repo.dao.summaries().collectAsState(initial = null)
    var query by remember { mutableStateOf("") }
    val saved = remember { SortPrefs.load(ctx) }
    var sort by remember { mutableStateOf(saved.first) }
    var desc by remember { mutableStateOf(saved.second) }

    val list = all ?: emptyList()
    val total = list.sumOf { it.balance }
    val debtors = list.count { it.balance > 0 }
    val q = Fa.norm(query)
    val shown = sortCustomers(
        if (q.isEmpty()) list else list.filter { Fa.norm(it.name).contains(q) },
        sort, desc
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر حساب", fontWeight = FontWeight.Bold) },
                actions = {
                    SyncBadge(repo, onSettings)
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "تنظیمات") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("بدهی جدید") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("جمع کل طلب", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Fa.toman(total),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            Fa.toFa("$debtors") + " مشتری بدهکار",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("جستجوی اسم") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "پاک کردن") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ترتیب:", style = MaterialTheme.typography.labelLarge)
                    SortBy.entries.forEach { s ->
                        val sel = sort == s
                        FilterChip(
                            selected = sel,
                            onClick = {
                                if (sel) desc = !desc else {
                                    sort = s
                                    desc = s.defaultDesc
                                }
                                SortPrefs.save(ctx, sort, desc)
                            },
                            label = { Text(s.title) },
                            trailingIcon = if (sel) {
                                {
                                    Icon(
                                        if (desc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                        if (desc) "نزولی" else "صعودی",
                                        Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
            if (all != null && shown.isEmpty()) {
                item {
                    Text(
                        if (list.isEmpty()) "هنوز بدهی‌ای ثبت نشده.\nبا دکمه‌ی «بدهی جدید» شروع کن."
                        else "مشتری‌ای با این اسم پیدا نشد.",
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            items(shown, key = { it.id }) { c ->
                CustomerRow(c) { onOpen(c.id) }
            }
        }
    }
}

@Composable
private fun CustomerRow(c: CustomerSummary, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (c.firstDebt != null && c.lastDebt != null) {
                    Text(
                        "اولین: " + Jalali.date(c.firstDebt) + "   آخرین: " + Jalali.date(c.lastDebt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            BalanceText(c.balance)
        }
    }
}

@Composable
fun BalanceText(balance: Long, big: Boolean = false) {
    Column(horizontalAlignment = Alignment.End) {
        val style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        when {
            balance > 0 -> Text(Fa.toman(balance), style = style, fontWeight = FontWeight.Bold, color = debtColor())
            balance < 0 -> {
                Text(Fa.toman(-balance), style = style, fontWeight = FontWeight.Bold, color = payColor())
                Text("طلبکار", style = MaterialTheme.typography.labelSmall, color = payColor())
            }
            else -> Text("تسویه", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
