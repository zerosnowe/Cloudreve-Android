package com.zerostudio.cloudreve.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalPageBottomPadding = compositionLocalOf { 96.dp }

val PageBottomPadding: Dp
    @Composable get() = LocalPageBottomPadding.current

val LocalCloudreveTopAppBarScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }

private val UnifiedTitlePadding = 16.dp
private val UnifiedIconPadding = 16.dp

@Composable
fun CloudreveScaffold(
    currentDestination: CloudreveDestination,
    onDestinationSelected: (CloudreveDestination) -> Unit,
    modifier: Modifier = Modifier,
    showBottomBar: Boolean = true,
    showTopBar: Boolean = true,
    topBarTitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val resolvedTopBarTitle = topBarTitle ?: stringResource(currentDestination.titleRes)
    val backgroundColor = MiuixTheme.colorScheme.background

    CompositionLocalProvider(
        LocalPageBottomPadding provides if (showBottomBar) 96.dp else 24.dp,
        LocalCloudreveTopAppBarScrollBehavior provides scrollBehavior,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (showTopBar) {
                        UnifiedTopAppBar(
                            title = resolvedTopBarTitle,
                            scrollBehavior = scrollBehavior,
                            actions = actions,
                        )
                    }
                },
                bottomBar = {
                    if (showBottomBar) {
                        BlurredNavigationBar(
                            selected = currentDestination,
                            onSelected = onDestinationSelected,
                        )
                    }
                },
                containerColor = Color.Transparent,
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                ) {
                    content(padding)
                }
            }
        }
    }
}

@Composable
fun UnifiedTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior?,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        largeTitle = title,
        modifier = modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surface,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        titlePadding = UnifiedTitlePadding,
        navigationIconPadding = UnifiedIconPadding,
        actionIconPadding = UnifiedIconPadding,
        actions = { actions() },
    )
}

@Composable
fun BlurredTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior?,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    UnifiedTopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
fun BlurredNavigationBar(
    selected: CloudreveDestination,
    onSelected: (CloudreveDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surface,
        showDivider = true,
        defaultWindowInsetsPadding = true,
    ) {
        CloudreveDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = destination.icon(),
                label = stringResource(destination.labelRes),
            )
        }
    }
}
