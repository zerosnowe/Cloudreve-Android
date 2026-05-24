package com.zerostudio.cloudreve.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.ui.LocalCloudreveTopAppBarScrollBehavior
import com.zerostudio.cloudreve.core.ui.PageBottomPadding
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsRoute(
    padding: PaddingValues,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        padding = padding,
        language = state.language,
        onLanguageChange = viewModel::setLanguage,
        onOpenAbout = onOpenAbout,
        onSignOut = viewModel::signOut,
    )
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = LocalCloudreveTopAppBarScrollBehavior.current
    val scrollModifier = if (scrollBehavior != null) {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        Modifier
    }
    val languageOptions = AppLanguage.entries
    val languageLabels = languageOptions.map { option ->
        when (option) {
            AppLanguage.SimplifiedChinese -> stringResource(R.string.settings_language_simplified_chinese)
            AppLanguage.English -> stringResource(R.string.settings_language_english)
        }
    }
    val selectedLanguageIndex = languageOptions.indexOf(language).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .overScrollVertical()
                .then(scrollModifier),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = PageBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(contentType = "settings_preferences") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownPreference(
                            items = languageLabels,
                            selectedIndex = selectedLanguageIndex,
                            title = stringResource(R.string.settings_language_title),
                            summary = stringResource(R.string.settings_language_summary),
                            insideMargin = BasicComponentDefaults.InsideMargin,
                            onSelectedIndexChange = { index ->
                                languageOptions.getOrNull(index)?.let(onLanguageChange)
                            },
                        )
                        BasicComponent(
                            title = stringResource(R.string.settings_security_title),
                            summary = stringResource(R.string.settings_security_summary),
                            insideMargin = BasicComponentDefaults.InsideMargin,
                            enabled = true,
                        )
                        BasicComponent(
                            title = stringResource(R.string.settings_storage_title),
                            summary = stringResource(R.string.settings_storage_summary),
                            insideMargin = BasicComponentDefaults.InsideMargin,
                            enabled = true,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_about_title),
                            summary = stringResource(R.string.settings_about_summary),
                            insideMargin = BasicComponentDefaults.InsideMargin,
                            onClick = onOpenAbout,
                        )
                        BasicComponent(
                            title = stringResource(R.string.settings_sign_out),
                            titleColor = BasicComponentDefaults.titleColor(
                                color = MiuixTheme.colorScheme.error,
                                disabledColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            ),
                            summary = stringResource(R.string.settings_sign_out_summary),
                            insideMargin = BasicComponentDefaults.InsideMargin,
                            onClick = onSignOut,
                            enabled = true,
                        )
                    }
                }
            }
        }
    }
}
