@file:OptIn(ExperimentalMaterial3Api::class)

package com.arash.daftar

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Sync.init(this)
        // موقع باز شدن: تغییرهای ارسال‌نشده فرستاده و اطلاعات سرور گرفته شود
        // (روی گوشی جدید همین‌جا همه‌چیز از Supabase برمی‌گردد)
        Sync.request(this)
    }
}

sealed class Screen {
    data object Home : Screen()
    data object Add : Screen()
    data object Settings : Screen()
    data class Detail(val id: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(repo)
                }
            }
        }
    }
}

@Composable
fun Root(repo: Repo) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    BackHandler(enabled = stack.size > 1) { back() }

    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(
            repo, snackbar,
            onOpen = { stack.add(Screen.Detail(it)) },
            onAdd = { stack.add(Screen.Add) },
            onSettings = { stack.add(Screen.Settings) }
        )
        Screen.Add -> AddDebtScreen(repo, scope) { back() }
        Screen.Settings -> SettingsScreen(repo, scope) { back() }
        is Screen.Detail -> DetailScreen(repo, s.id, snackbar, scope) { back() }
    }
}

@Composable
fun SettingsScreen(repo: Repo, scope: CoroutineScope, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by Sync.status.collectAsState()
    val pending by repo.dao.pendingCount().collectAsState(initial = 0)
    var askRestore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "بازگشت") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("بکاپ روی سرور (Supabase)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val line = when {
                        !Sync.configured -> "این نسخه به سرور وصل نیست."
                        st.running -> "در حال بکاپ…"
                        pending == 0 && st.error == null -> "همه‌چیز بکاپ شده است ✓"
                        pending > 0 -> Fa.toFa("$pending") + " تغییر منتظر ارسال است"
                        else -> "آخرین تلاش ناموفق بود"
                    }
                    Text(line, style = MaterialTheme.typography.bodyLarge)
                    st.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (st.lastOk > 0) {
                        Text(
                            "آخرین بکاپ موفق: " + Jalali.date(st.lastOk) + " ساعت " + Jalali.time(st.lastOk),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(enabled = !st.running && Sync.configured, onClick = {
                        scope.launch { runCatching { Sync.run(ctx) } }
                    }) { Text("بکاپ همین الان") }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(enabled = !st.running && Sync.configured, onClick = { askRestore = true }) {
                        Text("دریافت دوباره‌ی همه‌ی اطلاعات از سرور")
                    }
                }
            }
            UpdateSection()
        }
    }

    if (askRestore) {
        AlertDialog(
            onDismissRequest = { askRestore = false },
            title = { Text("دریافت از سرور") },
            text = {
                Text(
                    "همه‌ی اطلاعات از سرور دوباره گرفته می‌شود. " +
                        "تغییرهای این گوشی که هنوز ارسال نشده‌اند اول فرستاده می‌شوند و از بین نمی‌روند."
                )
            },
            confirmButton = {
                Button(onClick = {
                    askRestore = false
                    Sync.resetPull(ctx)
                    scope.launch { runCatching { Sync.run(ctx) } }
                }) { Text("دریافت") }
            },
            dismissButton = { TextButton(onClick = { askRestore = false }) { Text("انصراف") } }
        )
    }
}
