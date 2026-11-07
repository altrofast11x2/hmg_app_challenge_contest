package com.altrostudio.drivelog.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.TopBar
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** 튜토리얼 한 장 */
private data class TutorialPage(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

private val PAGES = listOf(
    TutorialPage(DriveLogIcons.Odometer, R.string.tutorial_1_title, R.string.tutorial_1_body),
    TutorialPage(DriveLogIcons.Mic, R.string.tutorial_2_title, R.string.tutorial_2_body),
    TutorialPage(DriveLogIcons.Person, R.string.tutorial_3_title, R.string.tutorial_3_body),
    TutorialPage(DriveLogIcons.Help, R.string.tutorial_4_title, R.string.tutorial_4_body)
)

/**
 * 처음 쓰는 운전자를 위한 사용법 네 장 (2026-11-08 사용자 요청). 동의·연결 뒤 한 번 보여 주고, 설정의 [사용법 다시 보기]로 다시 연다.
 * 장이 바뀔 때마다 [onSpeak]로 제목과 설명을 읽는다 (음성 안내를 껐으면 읽지 않는다).
 * A11 명세가 나오면 명세대로 바꾼다 (지금은 토큰만 적용)
 */
@Composable
fun TutorialScreen(onSpeak: (String) -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = PAGES[index]
    val last = index == PAGES.lastIndex
    LaunchedEffect(index) {
        onSpeak(context.getString(page.title) + ". " + context.getString(page.body).replace("[", "").replace("]", ""))
    }
    ScreenFrame(modifier = modifier) { portrait ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
            TopBar(title = stringResource(R.string.tutorial_title, index + 1, PAGES.size), portrait = portrait)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium, Alignment.CenterVertically)
            ) {
                Box(
                    modifier = Modifier.size(dimens.buttonHeightLarge).clip(CircleShape).background(colors.surfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(dimens.iconSizeLarge))
                }
                Text(stringResource(page.title), style = typography.headline, color = colors.textPrimary)
                Text(
                    stringResource(page.body),
                    style = typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.widthIn(max = dimens.contentMaxWidth / 2)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)) {
                    PAGES.indices.forEach { i ->
                        Box(
                            Modifier.size(dimens.spacingSmall).clip(RoundedCornerShape(dimens.radiusFull))
                                .background(if (i == index) colors.accent else colors.border)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!last) DriveLogButton(stringResource(R.string.tutorial_skip), onFinish, kind = ButtonKind.Text)
                Spacer(Modifier.weight(1f))
                if (index > 0) {
                    DriveLogButton(stringResource(R.string.tutorial_prev), { index-- }, kind = ButtonKind.Secondary, modifier = Modifier.padding(end = dimens.spacingSmall))
                }
                DriveLogButton(
                    stringResource(if (last) R.string.tutorial_start else R.string.tutorial_next),
                    { if (last) onFinish() else index++ }
                )
            }
        }
    }
}
