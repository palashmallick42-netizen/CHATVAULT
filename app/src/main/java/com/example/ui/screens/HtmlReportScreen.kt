package com.example.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.local.CapturedMessageEntity
import com.example.data.repository.HtmlReportBuilder
import com.example.ui.viewmodel.HtmlClarifyUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HtmlReportScreen(
    title: String,
    messages: List<CapturedMessageEntity>,
    allowAllVsDeletedToggle: Boolean,
    clarifyState: HtmlClarifyUiState,
    onBack: () -> Unit,
    onClarifyWithAi: (List<CapturedMessageEntity>) -> Unit,
    onDismissClarifyError: () -> Unit,
    onShareHtml: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var deletedOnlyInHtml by remember(allowAllVsDeletedToggle) { mutableStateOf(true) }
    val systemDark = isSystemInDarkTheme()
    var darkHtmlTheme by remember(systemDark) { mutableStateOf(systemDark) }

    val displayedMessages = remember(messages, deletedOnlyInHtml, allowAllVsDeletedToggle) {
        if (!allowAllVsDeletedToggle || deletedOnlyInHtml) {
            messages.filter { it.isDeleted }.ifEmpty {
                if (allowAllVsDeletedToggle) emptyList() else messages
            }
        } else {
            messages
        }
    }

    val clarifiedText = (clarifyState as? HtmlClarifyUiState.Success)?.summary
    val htmlContent = remember(title, displayedMessages, clarifiedText, darkHtmlTheme) {
        HtmlReportBuilder.buildHtmlReport(
            reportTitle = title,
            messages = displayedMessages,
            clarifiedSummary = clarifiedText,
            darkTheme = darkHtmlTheme
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "HTML View · ${displayedMessages.size} message(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("html_report_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { darkHtmlTheme = !darkHtmlTheme },
                        modifier = Modifier.testTag("html_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (darkHtmlTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle HTML theme"
                        )
                    }
                    IconButton(
                        onClick = { onShareHtml(htmlContent) },
                        modifier = Modifier.testTag("html_report_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Export HTML"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allowAllVsDeletedToggle) {
                        FilterChip(
                            selected = deletedOnlyInHtml,
                            onClick = { deletedOnlyInHtml = true },
                            label = { Text("Deleted Only") },
                            modifier = Modifier.testTag("html_filter_deleted")
                        )
                        FilterChip(
                            selected = !deletedOnlyInHtml,
                            onClick = { deletedOnlyInHtml = false },
                            label = { Text("All Messages") },
                            modifier = Modifier.testTag("html_filter_all")
                        )
                    }
                    FilledTonalButton(
                        onClick = { onClarifyWithAi(displayedMessages) },
                        enabled = displayedMessages.isNotEmpty() && clarifyState !is HtmlClarifyUiState.Loading,
                        modifier = Modifier.testTag("html_clarify_ai_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (clarifyState is HtmlClarifyUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (clarifyState is HtmlClarifyUiState.Loading) "Clarifying…" else "Make Clear with AI"
                        )
                    }
                }
            }

            if (clarifyState is HtmlClarifyUiState.Error) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = clarifyState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissClarifyError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("html_report_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        tag = htmlContent
                        loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    if (webView.tag != htmlContent) {
                        webView.tag = htmlContent
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    }
                }
            )
        }
    }
}
