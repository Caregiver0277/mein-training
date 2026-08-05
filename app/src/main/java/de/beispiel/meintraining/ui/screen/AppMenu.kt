package de.beispiel.meintraining.ui.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.MenuButtonIcon
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary

/** Bereiche des Menüs. */
enum class MenuDestination(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int) {
    TRACKING(R.string.drawer_tracking, R.drawable.ic_tracking),
    STATS(R.string.drawer_stats, R.drawable.ic_stats),
    HISTORY(R.string.drawer_history, R.drawable.ic_history),
    DELOAD(R.string.drawer_deload, R.drawable.ic_deload),
    SETTINGS(R.string.drawer_settings, R.drawable.ic_settings),
    ABOUT(R.string.drawer_about, R.drawable.ic_info)
}

/**
 * Kompaktes Menü direkt am Menüknopf statt einer über den halben Bildschirm gezogenen
 * Schublade – es nimmt nur so viel Platz ein, wie die Einträge brauchen.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    selected: MenuDestination?,
    onDismiss: () -> Unit,
    onDestinationClick: (MenuDestination) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = Dimens.CornerCard,
        containerColor = CardBackground
    ) {
        MenuDestination.entries.forEach { destination ->
            val isSelected = destination == selected
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(destination.titleRes),
                        style = AppTextStyles.TabLabel,
                        color = if (isSelected) AccentBlue else TextPrimary
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                        tint = if (isSelected) AccentBlue else TextSecondary,
                        modifier = Modifier.size(Dimens.MenuIconSize)
                    )
                },
                onClick = { onDestinationClick(destination) }
            )
        }
    }
}

/**
 * Kopfzeile der Unterseiten: Zurück-Pfeil und Titel.
 *
 * [actions] steht ganz rechts, falls die Seite eigene Knöpfe mitbringt – der Verlauf etwa das
 * „+“ zum Nachtragen. Ein leerer Bereich kostet nichts, deshalb gibt es dafür keine zweite
 * Kopfzeile.
 */
@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTargetSize)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MenuButtonIcon,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
        }
        Text(
            text = title,
            style = AppTextStyles.Title,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.SectionSpacingSmall)
        )
        actions()
    }
}

/** Platzhalter für die noch nicht gebauten Bereiche des Menüs. */
@Composable
fun PlaceholderScreen(
    destination: MenuDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(destination.titleRes), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(Dimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.placeholder_body),
                style = AppTextStyles.Body,
                color = TextSecondary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun SubScreenHeaderPreview() {
    MeinTrainingTheme {
        SubScreenHeader(title = "Verlauf", onBack = {})
    }
}
