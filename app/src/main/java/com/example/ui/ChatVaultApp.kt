package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.RecoveryEventRow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.service.NotificationHelper
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.HtmlReportScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.MediaVaultScreen
import com.example.ui.screens.NumberLookupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AiChatViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

object ChatVaultRoutes {
    const val CHATS = "chats"
    const val NUMBER_LOOKUP = "number_lookup"
    const val MEDIA = "media"
    const val AI_CHAT = "ai_chat"
    const val STUDIO = "studio"
    const val SETTINGS = "settings"
    const val HTML_REPORT_ALL = "html_report_all"
    const val HTML_REPORT_CHAT_PATTERN = "html_report/{chatKey}/{chatTitle}"
    const val HTML_REPORT_NUMBER_PATTERN = "html_report_number/{phoneLabel}"
    const val CHAT_DETAIL_PATTERN = "chat_detail/{chatKey}/{chatTitle}"

    fun chatDetail(chatKey: String, chatTitle: String): String =
        "chat_detail/${Uri.encode(chatKey)}/${Uri.encode(chatTitle)}"

    fun htmlReportForChat(chatKey: String, chatTitle: String): String =
        "html_report/${Uri.encode(chatKey)}/${Uri.encode(chatTitle)}"

    fun htmlReportForNumber(phoneLabel: String): String =
        "html_report_number/${Uri.encode(phoneLabel)}"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(
        route = ChatVaultRoutes.CHATS,
        labelRes = R.string.nav_chats,
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum,
        testTag = "nav_tab_chats"
    ),
    TopLevelDestination(
        route = ChatVaultRoutes.NUMBER_LOOKUP,
        labelRes = R.string.nav_lookup,
        selectedIcon = Icons.Filled.Phone,
        unselectedIcon = Icons.Outlined.Phone,
        testTag = "nav_tab_lookup"
    ),
    TopLevelDestination(
        route = ChatVaultRoutes.MEDIA,
        labelRes = R.string.nav_media,
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
        testTag = "nav_tab_media"
    ),
    TopLevelDestination(
        route = ChatVaultRoutes.AI_CHAT,
        labelRes = R.string.nav_assistant,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_ai_chat"
    ),
    TopLevelDestination(
        route = ChatVaultRoutes.STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.Brush,
        unselectedIcon = Icons.Outlined.Brush,
        testTag = "nav_tab_studio"
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatVaultApp(
    vaultViewModel: VaultViewModel = viewModel(factory = VaultViewModel.Factory),
    aiChatViewModel: AiChatViewModel = viewModel(factory = AiChatViewModel.Factory),
    imageStudioViewModel: ImageStudioViewModel = viewModel(factory = ImageStudioViewModel.Factory)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dashboardState by vaultViewModel.dashboardState.collectAsStateWithLifecycle()
    val chats by vaultViewModel.filteredChats.collectAsStateWithLifecycle()
    val chatSearchQuery by vaultViewModel.chatSearchQuery.collectAsStateWithLifecycle()
    val showDeletedChatsOnly by vaultViewModel.showDeletedChatsOnly.collectAsStateWithLifecycle()
    val mediaItems by vaultViewModel.filteredMedia.collectAsStateWithLifecycle()
    val showDeletedMediaOnly by vaultViewModel.showDeletedMediaOnly.collectAsStateWithLifecycle()
    val deletedMessagesFeed by vaultViewModel.deletedMessagesFeed.collectAsStateWithLifecycle()
    val htmlClarifyState by vaultViewModel.htmlClarifyState.collectAsStateWithLifecycle()
    val recentEvents by vaultViewModel.recentEvents.collectAsStateWithLifecycle()
    val lookupPhoneInput by vaultViewModel.lookupPhoneInput.collectAsStateWithLifecycle()
    val numberLookupResult by vaultViewModel.numberLookupResult.collectAsStateWithLifecycle()
    var showActivitySheet by remember { mutableStateOf(false) }

    val aiMessages by aiChatViewModel.messages.collectAsStateWithLifecycle()
    val aiSelectedProfile by aiChatViewModel.selectedProfile.collectAsStateWithLifecycle()
    val aiStreamState by aiChatViewModel.streamState.collectAsStateWithLifecycle()

    val studioSize by imageStudioViewModel.selectedSize.collectAsStateWithLifecycle()
    val studioRatio by imageStudioViewModel.selectedRatio.collectAsStateWithLifecycle()
    val studioReferenceBitmap by imageStudioViewModel.referenceBitmap.collectAsStateWithLifecycle()
    val studioUiState by imageStudioViewModel.uiState.collectAsStateWithLifecycle()
    val studioActionMessage by imageStudioViewModel.actionMessage.collectAsStateWithLifecycle()

    // Refresh listener & permission status whenever user returns from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vaultViewModel.refreshPermissionsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(dashboardState.statusBannerMessage) {
        val msg = dashboardState.statusBannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vaultViewModel.clearStatusBanner()
    }

    LaunchedEffect(studioActionMessage) {
        val msg = studioActionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        imageStudioViewModel.clearActionMessage()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        vaultViewModel.refreshPermissionsStatus()
    }

    val safFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            vaultViewModel.onSafFolderLinked(uri)
        }
    }

    val vaultPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vaultViewModel.importPickedPhoto(uri)
        }
    }

    val studioReferencePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageStudioViewModel.loadReferenceFromUri(uri)
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTopLevelRoute = TOP_LEVEL_DESTINATIONS.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (isTopLevelRoute) {
                    TopAppBar(
                        title = {
                            val titleRes = TOP_LEVEL_DESTINATIONS
                                .firstOrNull { dest ->
                                    currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                }?.labelRes ?: R.string.app_name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " · ${stringResource(titleRes)}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showActivitySheet = true },
                                modifier = Modifier.testTag("open_activity_log_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = "Recovery activity log"
                                )
                            }
                            IconButton(
                                onClick = { navController.navigate(ChatVaultRoutes.SETTINGS) },
                                modifier = Modifier.testTag("open_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.cd_settings)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute && !useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (isTopLevelRoute && useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ChatVaultRoutes.CHATS,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 820.dp)
                    ) {
                        composable(ChatVaultRoutes.CHATS) {
                            ChatsScreen(
                                dashboardState = dashboardState,
                                chats = chats,
                                searchQuery = chatSearchQuery,
                                showDeletedOnly = showDeletedChatsOnly,
                                onSearchChange = vaultViewModel::updateSearchQuery,
                                onToggleDeletedOnly = vaultViewModel::setShowDeletedChatsOnly,
                                onChatClick = { summary ->
                                    navController.navigate(
                                        ChatVaultRoutes.chatDetail(summary.chatKey, summary.chatTitle)
                                    )
                                },
                                onTogglePin = { summary ->
                                    vaultViewModel.toggleThreadPinned(summary.chatKey, !summary.isPinned)
                                },
                                onOpenDeletedHtml = {
                                    vaultViewModel.clearHtmlClarifyState()
                                    navController.navigate(ChatVaultRoutes.HTML_REPORT_ALL)
                                },
                                onOpenNumberLookup = {
                                    navigateTopLevel(ChatVaultRoutes.NUMBER_LOOKUP)
                                },
                                onEnableListenerClick = { NotificationHelper.openListenerSettings(context) },
                                onRequestPostNotificationsClick = requestNotificationPermission,
                                onLinkFolderClick = { safFolderPickerLauncher.launch(null) },
                                onSendTestNotification = {
                                    vaultViewModel.sendDiagnosticTestNotification(isDeleteSignal = false)
                                }
                            )
                        }

                        composable(ChatVaultRoutes.NUMBER_LOOKUP) {
                            NumberLookupScreen(
                                phoneInput = lookupPhoneInput,
                                result = numberLookupResult,
                                onPhoneInputChange = vaultViewModel::updateLookupPhoneInput,
                                onOpenThread = { summary ->
                                    navController.navigate(
                                        ChatVaultRoutes.chatDetail(summary.chatKey, summary.chatTitle)
                                    )
                                },
                                onLinkThreadToNumber = vaultViewModel::linkPhoneNumberToThread,
                                onUnlinkThreadNumber = vaultViewModel::unlinkPhoneNumberFromThread,
                                onOpenHtmlForNumber = { phoneLabel ->
                                    vaultViewModel.clearHtmlClarifyState()
                                    navController.navigate(
                                        ChatVaultRoutes.htmlReportForNumber(phoneLabel)
                                    )
                                },
                                onAnalyzeNumberWithAi = { phoneLabel, matchedMessages ->
                                    val transcript = vaultViewModel.buildNumberTranscript(
                                        phoneLabel,
                                        matchedMessages
                                    )
                                    aiChatViewModel.analyzeRecoveredTranscript(transcript)
                                    navigateTopLevel(ChatVaultRoutes.AI_CHAT)
                                },
                                onOpenInWhatsApp = { phone ->
                                    vaultViewModel.buildOpenWhatsAppIntent(phone)?.let { intent ->
                                        runCatching { context.startActivity(intent) }
                                    }
                                }
                            )
                        }

                        composable(
                            route = ChatVaultRoutes.HTML_REPORT_NUMBER_PATTERN,
                            arguments = listOf(navArgument("phoneLabel") { type = NavType.StringType })
                        ) { entry ->
                            val phoneLabel = Uri.decode(entry.arguments?.getString("phoneLabel").orEmpty())
                            HtmlReportScreen(
                                title = "WhatsApp: $phoneLabel",
                                messages = numberLookupResult.matchingMessages,
                                allowAllVsDeletedToggle = true,
                                clarifyState = htmlClarifyState,
                                onBack = { navController.popBackStack() },
                                onClarifyWithAi = vaultViewModel::clarifyMessagesForHtml,
                                onDismissClarifyError = vaultViewModel::clearHtmlClarifyState,
                                onShareHtml = { html ->
                                    scope.launch {
                                        val shareIntent = vaultViewModel.buildHtmlShareIntent(
                                            html,
                                            phoneLabel
                                        )
                                        safeStartChooser(context, shareIntent, "Export HTML Report")
                                    }
                                }
                            )
                        }

                        composable(ChatVaultRoutes.HTML_REPORT_ALL) {
                            HtmlReportScreen(
                                title = "Deleted WhatsApp Chats",
                                messages = deletedMessagesFeed,
                                allowAllVsDeletedToggle = false,
                                clarifyState = htmlClarifyState,
                                onBack = { navController.popBackStack() },
                                onClarifyWithAi = vaultViewModel::clarifyMessagesForHtml,
                                onDismissClarifyError = vaultViewModel::clearHtmlClarifyState,
                                onShareHtml = { html ->
                                    scope.launch {
                                        val shareIntent = vaultViewModel.buildHtmlShareIntent(
                                            html,
                                            "deleted_chats"
                                        )
                                        safeStartChooser(context, shareIntent, "Export HTML Report")
                                    }
                                }
                            )
                        }

                        composable(
                            route = ChatVaultRoutes.HTML_REPORT_CHAT_PATTERN,
                            arguments = listOf(
                                navArgument("chatKey") { type = NavType.StringType },
                                navArgument("chatTitle") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val chatKey = Uri.decode(entry.arguments?.getString("chatKey").orEmpty())
                            val chatTitle = Uri.decode(entry.arguments?.getString("chatTitle").orEmpty())
                            val messages by vaultViewModel
                                .observeMessagesForChat(chatKey)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            HtmlReportScreen(
                                title = "$chatTitle — Clear Report",
                                messages = messages,
                                allowAllVsDeletedToggle = true,
                                clarifyState = htmlClarifyState,
                                onBack = { navController.popBackStack() },
                                onClarifyWithAi = vaultViewModel::clarifyMessagesForHtml,
                                onDismissClarifyError = vaultViewModel::clearHtmlClarifyState,
                                onShareHtml = { html ->
                                    scope.launch {
                                        val shareIntent = vaultViewModel.buildHtmlShareIntent(
                                            html,
                                            chatTitle
                                        )
                                        safeStartChooser(context, shareIntent, "Export HTML Report")
                                    }
                                }
                            )
                        }

                        composable(
                            route = ChatVaultRoutes.CHAT_DETAIL_PATTERN,
                            arguments = listOf(
                                navArgument("chatKey") { type = NavType.StringType },
                                navArgument("chatTitle") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val chatKey = Uri.decode(entry.arguments?.getString("chatKey").orEmpty())
                            val chatTitle = Uri.decode(entry.arguments?.getString("chatTitle").orEmpty())
                            val messages by vaultViewModel
                                .observeMessagesForChat(chatKey)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            ChatDetailScreen(
                                chatTitle = chatTitle,
                                messages = messages,
                                onBack = { navController.popBackStack() },
                                onExportChat = {
                                    scope.launch {
                                        val shareIntent = vaultViewModel.buildChatExportIntent(chatKey, chatTitle)
                                        safeStartChooser(context, shareIntent, "Export Chat")
                                    }
                                },
                                onAnalyzeWithAi = {
                                    scope.launch {
                                        val transcript = vaultViewModel.buildChatTranscriptText(chatKey, chatTitle)
                                        aiChatViewModel.analyzeRecoveredTranscript(transcript)
                                        navigateTopLevel(ChatVaultRoutes.AI_CHAT)
                                    }
                                },
                                onOpenHtmlView = {
                                    vaultViewModel.clearHtmlClarifyState()
                                    navController.navigate(
                                        ChatVaultRoutes.htmlReportForChat(chatKey, chatTitle)
                                    )
                                },
                                onDeleteThread = { vaultViewModel.deleteChatThread(chatKey) },
                                onTriggerDiagnosticDelete = {
                                    vaultViewModel.sendDiagnosticTestNotification(isDeleteSignal = true)
                                }
                            )
                        }

                        composable(ChatVaultRoutes.MEDIA) {
                            MediaVaultScreen(
                                dashboardState = dashboardState,
                                mediaItems = mediaItems,
                                showDeletedOnly = showDeletedMediaOnly,
                                onToggleDeletedOnly = vaultViewModel::setShowDeletedMediaOnly,
                                onLinkFolderClick = { safFolderPickerLauncher.launch(null) },
                                onSyncNowClick = vaultViewModel::syncLinkedMediaFolder,
                                onImportPhotoClick = {
                                    vaultPhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onSaveToGallery = vaultViewModel::saveMediaToGallery,
                                onShareMedia = { item ->
                                    vaultViewModel.createMediaShareIntent(item)?.let { shareIntent ->
                                        safeStartChooser(context, shareIntent, "Share Recovered Media")
                                    }
                                },
                                onEnhanceInStudio = { item ->
                                    imageStudioViewModel.loadReferenceFromVaultPath(item.localVaultPath)
                                    navigateTopLevel(ChatVaultRoutes.STUDIO)
                                },
                                onDeleteMedia = vaultViewModel::deleteMediaFromVault
                            )
                        }

                        composable(ChatVaultRoutes.AI_CHAT) {
                            AiChatScreen(
                                messages = aiMessages,
                                selectedProfile = aiSelectedProfile,
                                streamState = aiStreamState,
                                onSelectProfile = aiChatViewModel::selectProfile,
                                onSendMessage = aiChatViewModel::sendMessage,
                                onClearHistory = aiChatViewModel::clearHistory,
                                onDismissError = aiChatViewModel::clearError
                            )
                        }

                        composable(ChatVaultRoutes.STUDIO) {
                            ImageStudioScreen(
                                selectedSize = studioSize,
                                selectedRatio = studioRatio,
                                referenceBitmap = studioReferenceBitmap,
                                uiState = studioUiState,
                                onSelectSize = imageStudioViewModel::selectSize,
                                onSelectRatio = imageStudioViewModel::selectRatio,
                                onPickReferencePhoto = {
                                    studioReferencePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearReferencePhoto = imageStudioViewModel::clearReferenceImage,
                                onGenerateClick = imageStudioViewModel::generateImage,
                                onSaveResultClick = imageStudioViewModel::saveCurrentResultToVaultAndGallery,
                                onShareResultClick = {
                                    scope.launch {
                                        imageStudioViewModel.createShareIntentForCurrentResult()?.let { shareIntent ->
                                            safeStartChooser(context, shareIntent, "Share Studio Image")
                                        }
                                    }
                                }
                            )
                        }

                        composable(ChatVaultRoutes.SETTINGS) {
                            SettingsScreen(
                                dashboardState = dashboardState,
                                recentEvents = recentEvents,
                                onBack = { navController.popBackStack() },
                                onOpenListenerSettings = { NotificationHelper.openListenerSettings(context) },
                                onOpenAppInfo = { NotificationHelper.openAppInfoSettings(context) },
                                onRequestPostNotifications = requestNotificationPermission,
                                onLinkFolderClick = { safFolderPickerLauncher.launch(null) },
                                onUnlinkFolderClick = vaultViewModel::unlinkSafFolder,
                                onToggleWhatsApp = vaultViewModel::setMonitorWhatsApp,
                                onToggleWhatsAppBusiness = vaultViewModel::setMonitorWhatsAppBusiness,
                                onToggleDiagnostic = vaultViewModel::setMonitorDiagnostic,
                                onToggleDeletedAlerts = vaultViewModel::setNotifyOnDeleted,
                                onSendTestMessageNotification = {
                                    vaultViewModel.sendDiagnosticTestNotification(isDeleteSignal = false)
                                },
                                onSendTestDeleteNotification = {
                                    vaultViewModel.sendDiagnosticTestNotification(isDeleteSignal = true)
                                },
                                onClearAllMessages = vaultViewModel::clearAllMessages
                            )
                        }
                    }
                }
            }
        }

        if (showActivitySheet) {
            ModalBottomSheet(
                onDismissRequest = { showActivitySheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Recovery Activity Timeline",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-time audit log of captured messages, deletions caught, and media recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (recentEvents.isEmpty()) {
                        Text(
                            text = "No recovery events recorded yet. Enable Notification Access or run a Test Capture to see live activity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentEvents, key = { it.id }) { event ->
                                RecoveryEventRow(event = event)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun safeStartChooser(context: Context, intent: Intent, title: String) {
    runCatching {
        context.startActivity(Intent.createChooser(intent, title))
    }
}
