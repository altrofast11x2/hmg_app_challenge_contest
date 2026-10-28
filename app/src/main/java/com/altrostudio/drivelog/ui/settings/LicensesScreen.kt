package com.altrostudio.drivelog.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** 앱에 들어간 라이브러리·글꼴 (release 빌드 기준, docs/licenses.md와 같게 유지) */
private data class OpenSourceLibrary(val name: String, val owner: String, val license: String)

private const val APACHE_2 = "Apache License 2.0"
private const val OFL_1_1 = "SIL Open Font License 1.1"
private const val BSD_ZETETIC = "BSD 계열 (SQLCipher Community Edition License)"

private val LIBRARIES = listOf(
    OpenSourceLibrary("Pleos Connect SDK (Vehicle, NaviHelper, TextToSpeech)", "Pleos", "Pleos Playground 이용약관"),
    OpenSourceLibrary("Asta Sans (글꼴)", "The Asta Sans Project Authors", OFL_1_1),
    OpenSourceLibrary("SQLCipher for Android (운행 기록 DB 암호화)", "Zetetic, LLC", BSD_ZETETIC),
    OpenSourceLibrary("AndroidX (Core, Activity, Lifecycle, Room, SQLite, WorkManager 외)", "The Android Open Source Project", APACHE_2),
    OpenSourceLibrary("OkHttp, Okio", "Square", APACHE_2),
    OpenSourceLibrary("ZXing core (QR 코드)", "ZXing authors", APACHE_2),
    OpenSourceLibrary("Jetpack Compose (UI, Foundation, Material 3)", "The Android Open Source Project", APACHE_2),
    OpenSourceLibrary("Kotlin Standard Library, kotlin-reflect", "JetBrains", APACHE_2),
    OpenSourceLibrary("kotlinx.coroutines, kotlinx.serialization", "JetBrains", APACHE_2),
    OpenSourceLibrary("Dagger, Hilt", "Google", APACHE_2),
    OpenSourceLibrary("Gson", "Google", APACHE_2),
    OpenSourceLibrary("Material Components for Android, Tink, Error Prone Annotations", "Google", APACHE_2),
    OpenSourceLibrary("JNA (Java Native Access)", "JNA authors", "$APACHE_2 (LGPL 2.1과의 이중 라이선스)"),
    OpenSourceLibrary("Guava ListenableFuture", "Google", APACHE_2),
    OpenSourceLibrary("JSR 305 Annotations", "FindBugs", APACHE_2),
    OpenSourceLibrary("javax.inject", "JSR-330", APACHE_2),
    OpenSourceLibrary("JetBrains Java Annotations, JSpecify", "JetBrains, JSpecify", APACHE_2)
)

/** 보고 있는 라이선스 원문 (없으면 목록) */
private enum class LicenseText(@RawRes val res: Int) {
    APACHE(R.raw.apache_license_2_0),
    OFL(R.raw.sil_ofl_1_1),
    SQLCIPHER(R.raw.sqlcipher_license)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var shown by rememberSaveable { mutableStateOf<LicenseText?>(null) }
    val context = LocalContext.current
    val licenseText = remember(shown) {
        shown?.let { text -> context.resources.openRawResource(text.res).bufferedReader().use { it.readText() } }
    }
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val colors = DriveLogTheme.colors
    ScreenScaffold(title = stringResource(R.string.licenses_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.licenses_intro), style = typography.body, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall), verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
            if (shown != null) {
                DriveLogButton(text = stringResource(R.string.licenses_back_to_list), onClick = { shown = null }, kind = ButtonKind.Secondary)
            } else {
                DriveLogButton(text = stringResource(R.string.licenses_show_apache), onClick = { shown = LicenseText.APACHE }, kind = ButtonKind.Secondary)
                DriveLogButton(text = stringResource(R.string.licenses_show_ofl), onClick = { shown = LicenseText.OFL }, kind = ButtonKind.Secondary)
                DriveLogButton(text = stringResource(R.string.licenses_show_sqlcipher), onClick = { shown = LicenseText.SQLCIPHER }, kind = ButtonKind.Secondary)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
            if (licenseText != null) {
                item { Text(licenseText, style = typography.labelSmall, color = colors.textSecondary) }
            } else {
                items(LIBRARIES) { library ->
                    Column {
                        Text(library.name, style = typography.titleSmall, color = colors.textPrimary)
                        Text("${library.owner} · ${library.license}", style = typography.label, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}
