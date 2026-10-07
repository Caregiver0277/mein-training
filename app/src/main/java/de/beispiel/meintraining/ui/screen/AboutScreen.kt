package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.BuildConfig
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.ScreenBackground
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary

/**
 * „Über die App“: wer sie ist, wie man sie bedient und wo die Daten liegen.
 *
 * Die Kurzanleitung ist der eigentliche Grund für diese Seite. Vieles in der App liegt auf
 * langem Druck – Auswahl, Zurücksetzen der Pausenuhr, Datenpunkte im Tracking –, und eine Geste,
 * die man nicht kennt, gibt es für einen nicht. Hier steht jede einmal an einer Stelle.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.drawer_about), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            IdentityCard()
            AboutCard(title = stringResource(R.string.about_guide_title)) {
                GuideGroup(
                    title = stringResource(R.string.about_guide_list),
                    items = listOf(
                        stringResource(R.string.about_guide_edit),
                        stringResource(R.string.about_guide_progress),
                        stringResource(R.string.about_guide_sets),
                        stringResource(R.string.about_guide_cardio),
                        stringResource(R.string.about_guide_cardio_list),
                        stringResource(R.string.about_guide_select),
                        stringResource(R.string.about_guide_check),
                        stringResource(R.string.about_guide_cycle)
                    )
                )
                GuideGroup(
                    title = stringResource(R.string.about_guide_timers),
                    items = listOf(
                        stringResource(R.string.about_guide_timer_toggle),
                        stringResource(R.string.about_guide_timer_config)
                    )
                )
                GuideGroup(
                    title = stringResource(R.string.about_guide_more),
                    items = listOf(
                        stringResource(R.string.about_guide_history),
                        stringResource(R.string.about_guide_tracking),
                        stringResource(R.string.about_guide_stats),
                        stringResource(R.string.about_guide_manage),
                        stringResource(R.string.about_guide_keep_screen_on)
                    )
                )
            }
            AboutCard(title = stringResource(R.string.about_data_title)) {
                Text(
                    text = stringResource(R.string.about_data_body),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

/** Symbol, Name und Version – woran man erkennt, welche Fassung auf dem Gerät läuft. */
@Composable
private fun IdentityCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Dasselbe Bild wie auf dem Startbildschirm, auf dessen Hintergrund.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(Dimens.AboutIconSize)
                    .clip(Dimens.CornerCard)
                    .background(ScreenBackground)
            )
            Column(modifier = Modifier.padding(start = Dimens.SectionSpacingMedium)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppTextStyles.Title,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
                )
            }
        }
        Text(
            text = stringResource(R.string.about_tagline),
            style = AppTextStyles.Body,
            color = TextSecondary
        )
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium)
    ) {
        Text(text = title, style = AppTextStyles.ExerciseName, color = TextPrimary)
        content()
    }
}

/** Eine Gruppe der Kurzanleitung: kleine Überschrift, darunter die Gesten als Aufzählung. */
@Composable
private fun GuideGroup(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)) {
        Text(text = title, style = AppTextStyles.ColumnLabel, color = AccentBlue)
        items.forEach { BulletPoint(text = it) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 1400)
@Composable
private fun AboutScreenPreview() {
    MeinTrainingTheme {
        AboutScreen(onBack = {})
    }
}
