package com.zerostudio.cloudreve.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AuthRoute(
    padding: PaddingValues,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(
        state = state,
        padding = padding,
        onServerUrlChange = viewModel::onServerUrlChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTwoFactorCodeChange = viewModel::onTwoFactorCodeChange,
        onSignIn = viewModel::signIn,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    padding: PaddingValues,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTwoFactorCodeChange: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    val errorText = state.errorMessage ?: if (state.errorRes != null) stringResource(state.errorRes) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.auth_connect_title), style = MiuixTheme.textStyles.title2)
        TextField(
            value = state.serverUrl,
            onValueChange = onServerUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.auth_server_url_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        TextField(
            value = state.username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.auth_email_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        TextField(
            value = state.password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.auth_password_label),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        TextField(
            value = state.twoFactorCode,
            onValueChange = onTwoFactorCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.auth_two_factor_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        errorText?.let {
            Text(it, color = MiuixTheme.colorScheme.error)
        }
        Button(
            onClick = onSignIn,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) {
                InfiniteProgressIndicator()
            } else {
                Text(stringResource(R.string.auth_sign_in_button))
            }
        }
    }
}
