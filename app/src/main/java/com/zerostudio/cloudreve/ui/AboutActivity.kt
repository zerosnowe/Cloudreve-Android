package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.R
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.core.ui.PageBottomPadding
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AboutActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val preferences = newBase.getSharedPreferences(
            APP_LANGUAGE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val language = AppLanguage.fromLanguageTag(preferences.getString(APP_LANGUAGE_TAG_KEY, null))
        super.attachBaseContext(newBase.createLocalizedContext(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableCloudreveImmersiveSystemBars()
        setContent {
            AboutApp(onBack = ::finish)
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }
}

private data class AboutFeatureEntry(
    val titleRes: Int,
    val summaryRes: Int,
)

private data class AboutReferenceEntry(
    val titleRes: Int,
    val summaryRes: Int,
    val url: String,
)

private val aboutFeatureEntries = listOf(
    AboutFeatureEntry(
        titleRes = R.string.about_feature_lyrics_title,
        summaryRes = R.string.about_feature_lyrics_summary,
    ),
    AboutFeatureEntry(
        titleRes = R.string.about_feature_editor_title,
        summaryRes = R.string.about_feature_editor_summary,
    ),
    AboutFeatureEntry(
        titleRes = R.string.about_feature_preview_title,
        summaryRes = R.string.about_feature_preview_summary,
    ),
    AboutFeatureEntry(
        titleRes = R.string.about_feature_stack_title,
        summaryRes = R.string.about_feature_stack_summary,
    ),
)

private val aboutReferenceEntries = listOf(
    AboutReferenceEntry(
        titleRes = R.string.about_reference_cloudreve_title,
        summaryRes = R.string.about_reference_cloudreve_summary,
        url = "https://docs.cloudreve.org/zh/api/",
    ),
    AboutReferenceEntry(
        titleRes = R.string.about_reference_miuix_title,
        summaryRes = R.string.about_reference_miuix_summary,
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    AboutReferenceEntry(
        titleRes = R.string.about_reference_lyrics_title,
        summaryRes = R.string.about_reference_lyrics_summary,
        url = "https://github.com/6xingyv/accompanist-lyrics-ui",
    ),
    AboutReferenceEntry(
        titleRes = R.string.about_reference_editor_title,
        summaryRes = R.string.about_reference_editor_summary,
        url = "https://github.com/burhanrashid52/PhotoEditor",
    ),
    AboutReferenceEntry(
        titleRes = R.string.about_reference_media3_title,
        summaryRes = R.string.about_reference_media3_summary,
        url = "https://developer.android.com/media/media3",
    ),
    AboutReferenceEntry(
        titleRes = R.string.about_reference_jetpack_title,
        summaryRes = R.string.about_reference_jetpack_summary,
        url = "https://developer.android.com/jetpack/androidx/explorer",
    ),
)

@Composable
private fun AboutApp(
    onBack: () -> Unit,
    languageController: AppLanguageController = koinInject(),
) {
    val language = languageController.currentLanguage.collectAsStateWithLifecycle().value
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.createLocalizedContext(language)
    }
    val localizedConfiguration = remember(localizedContext) {
        localizedContext.resources.configuration
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
    ) {
        MiuixCloudreveTheme {
            AboutScreen(onBack = onBack)
        }
    }
}

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val uriHandler = LocalUriHandler.current
    val landscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BackHandler(onBack = onBack)

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MiuixTheme.colorScheme.surface),
        ) {
            if (landscape) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        )
                        .padding(start = 64.dp, top = 32.dp, end = 64.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AboutIdentity(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        AboutFeaturesCard(modifier = Modifier.fillMaxWidth())
                        AboutReferencesCard(
                            modifier = Modifier.fillMaxWidth(),
                            onOpenUri = uriHandler::openUri,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 88.dp,
                        end = 16.dp,
                        bottom = PageBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(contentType = "identity") {
                        AboutIdentity(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    item(contentType = "features") {
                        AboutFeaturesCard(modifier = Modifier.fillMaxWidth())
                    }
                    item(contentType = "references") {
                        AboutReferencesCard(
                            modifier = Modifier.fillMaxWidth(),
                            onOpenUri = uriHandler::openUri,
                        )
                    }
                }
            }

            IconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(start = 16.dp, top = 6.dp),
                onClick = onBack,
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        }
    }
}

@Composable
private fun AboutIdentity(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionText = remember(context) {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        context.getString(
            R.string.about_version_format,
            packageInfo.versionName ?: "1.1beta",
            packageInfo.longVersionCode,
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    setImageResource(R.mipmap.ic_launcher_round)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = versionText,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun AboutFeaturesCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        cornerRadius = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            aboutFeatureEntries.forEach { entry ->
                BasicComponent(
                    title = stringResource(entry.titleRes),
                    summary = stringResource(entry.summaryRes),
                    insideMargin = BasicComponentDefaults.InsideMargin,
                    enabled = true,
                )
            }
        }
    }
}

@Composable
private fun AboutReferencesCard(
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        cornerRadius = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            aboutReferenceEntries.forEach { entry ->
                ArrowPreference(
                    title = stringResource(entry.titleRes),
                    summary = stringResource(entry.summaryRes),
                    insideMargin = BasicComponentDefaults.InsideMargin,
                    onClick = { onOpenUri(entry.url) },
                )
            }
        }
    }
}
