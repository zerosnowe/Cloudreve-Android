package com.zerostudio.cloudreve.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ShareRoute(
    padding: PaddingValues,
    viewModel: ShareViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShareScreen(
        state = state,
        padding = padding,
        onUriChange = viewModel::onUriChange,
        onCreateShare = viewModel::createShare,
    )
}

@Composable
fun ShareScreen(
    state: ShareUiState,
    padding: PaddingValues,
    onUriChange: (String) -> Unit,
    onCreateShare: () -> Unit,
) {
    val errorText = state.errorMessage ?: if (state.errorRes != null) stringResource(state.errorRes) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.share_create_title), style = MiuixTheme.textStyles.title3)
        TextField(
            value = state.uri,
            onValueChange = onUriChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.share_uri_label),
            singleLine = true,
        )
        Button(
            onClick = onCreateShare,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) InfiniteProgressIndicator() else Text(stringResource(R.string.share_create_button))
        }
        state.link?.let { Text(it, color = MiuixTheme.colorScheme.primary) }
        errorText?.let { Text(it, color = MiuixTheme.colorScheme.error) }
    }
}
