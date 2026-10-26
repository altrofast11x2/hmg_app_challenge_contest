package com.altrostudio.drivelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.theme.Dimens

/** 앱에 들어간 라이브러리 (release 빌드 의존성 기준, docs/licenses.md와 같게 유지) */
private data class OpenSourceLibrary(val name: String, val owner: String, val license: String)

private const val APACHE_2 = "Apache License 2.0"

private val LIBRARIES = listOf(
    OpenSourceLibrary("Pleos Connect SDK (Vehicle, NaviHelper)", "Pleos", "Pleos Playground 이용약관"),
    OpenSourceLibrary("AndroidX (Core, Activity, Lifecycle, Room, SQLite, WorkManager 외)", "The Android Open Source Project", APACHE_2),
    OpenSourceLibrary("OkHttp, Okio", "Square", APACHE_2),
    OpenSourceLibrary("Jetpack Compose (UI, Foundation, Material 3)", "The Android Open Source Project", APACHE_2),
    OpenSourceLibrary("Kotlin Standard Library, kotlin-reflect", "JetBrains", APACHE_2),
    OpenSourceLibrary("kotlinx.coroutines, kotlinx.serialization", "JetBrains", APACHE_2),
    OpenSourceLibrary("Dagger, Hilt", "Google", APACHE_2),
    OpenSourceLibrary("Gson", "Google", APACHE_2),
    OpenSourceLibrary("Guava ListenableFuture", "Google", APACHE_2),
    OpenSourceLibrary("JSR 305 Annotations", "FindBugs", APACHE_2),
    OpenSourceLibrary("javax.inject", "JSR-330", APACHE_2),
    OpenSourceLibrary("JetBrains Java Annotations, JSpecify", "JetBrains, JSpecify", APACHE_2)
)

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showApacheText by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val apacheText = remember {
        context.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
    }
    ScreenScaffold(title = stringResource(R.string.licenses_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyLarge)
        BigButton(
            text = stringResource(if (showApacheText) R.string.licenses_hide_apache else R.string.licenses_show_apache),
            onClick = { showApacheText = !showApacheText },
            emphasized = false
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            if (showApacheText) {
                item { Text(apacheText, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(LIBRARIES) { library ->
                    Column {
                        Text(library.name, style = MaterialTheme.typography.titleMedium)
                        Text("${library.owner} · ${library.license}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
