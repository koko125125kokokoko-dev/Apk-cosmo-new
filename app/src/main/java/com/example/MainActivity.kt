package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.CosmoViewModel
import com.example.ui.components.ActiveDownloadBanner
import com.example.ui.components.AppDetailSheet
import com.example.ui.components.FlatBadge
import com.example.ui.components.InstallDialog
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CosmoGameStoreTheme
import com.example.ui.theme.FlatAccentGreen

class MainActivity : ComponentActivity() {

    private val viewModel: CosmoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()

            CosmoGameStoreTheme(themePreference = themePreference) {
                // Request Notification permission on Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Result handled */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                CosmoApp(viewModel = viewModel)
            }
        }
    }
}

enum class CosmoTab(val title: String, val icon: ImageVector, val tag: String) {
    BROWSE("BROWSE", Icons.Default.SportsEsports, "tab_browse"),
    LIBRARY("MY LIBRARY", Icons.Default.Folder, "tab_library"),
    APPS("APPS", Icons.Default.Apps, "tab_apps"),
    SETTINGS("SETTINGS", Icons.Default.Settings, "tab_settings")
}

@Composable
fun CosmoApp(viewModel: CosmoViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val downloadedEntities by viewModel.downloadedEntities.collectAsStateWithLifecycle()
    val activeDownload by viewModel.activeDownload.collectAsStateWithLifecycle()
    val selectedAppForDetail by viewModel.selectedAppForDetail.collectAsStateWithLifecycle()
    val pendingInstallModel by viewModel.pendingInstallModel.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstalling.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val installMessage by viewModel.installMessage.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val autoLaunch by viewModel.autoLaunch.collectAsStateWithLifecycle()

    // Back press handler: if detail sheet open, close it; else if not on Browse, go to Browse; else system back
    BackHandler(enabled = selectedAppForDetail != null || selectedTab != 0) {
        if (selectedAppForDetail != null) {
            viewModel.selectAppForDetail(null)
        } else if (selectedTab != 0) {
            selectedTab = 0
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CosmoTopBar(currentTab = CosmoTab.values()[selectedTab])
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // In-App Download Progress Banner
                activeDownload?.let { download ->
                    ActiveDownloadBanner(
                        activeDownload = download,
                        onCancel = { viewModel.cancelActiveDownload(it) }
                    )
                }

                // Flat Bottom Navigation Bar
                CosmoBottomBar(
                    selectedTabIndex = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // Games (Browse Tab) - mainCategoryId == 2
                    val games = remember(allApps) {
                        allApps.filter { it.mainCategoryId == 2 || it.mainCategoryId == null }
                    }
                    BrowseScreen(
                        games = games,
                        categories = categories,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRefresh = { viewModel.loadCatalog() },
                        onGameSelected = { viewModel.selectAppForDetail(it) },
                        onDownloadGame = { viewModel.startDownload(it) }
                    )
                }

                1 -> {
                    // My Library
                    LibraryScreen(
                        downloadedEntities = downloadedEntities,
                        onInstallPackageModel = { viewModel.showInstallDialog(it) }
                    )
                }

                2 -> {
                    // Apps Tab - mainCategoryId == 1
                    val appsList = remember(allApps) {
                        allApps.filter { it.mainCategoryId == 1 }
                    }
                    AppsScreen(
                        apps = appsList,
                        categories = categories,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRefresh = { viewModel.loadCatalog() },
                        onAppSelected = { viewModel.selectAppForDetail(it) },
                        onDownloadApp = { viewModel.startDownload(it) }
                    )
                }

                3 -> {
                    // Settings Tab
                    SettingsScreen(
                        currentTheme = themePreference,
                        onThemeChanged = { viewModel.setTheme(it) },
                        autoLaunch = autoLaunch,
                        onAutoLaunchChanged = { viewModel.setAutoLaunch(it) }
                    )
                }
            }
        }
    }

    // App Detail Bottom Sheet
    selectedAppForDetail?.let { app ->
        val isInstalled = remember(app.downloadUrl) {
            // Check by packageName from download url or name
            val pkg = app.downloadUrl?.substringAfterLast("/")?.substringBefore("?") ?: ""
            viewModel.isAppInstalled(pkg)
        }

        AppDetailSheet(
            app = app,
            isInstalled = isInstalled,
            onDownload = {
                viewModel.startDownload(app)
                viewModel.selectAppForDetail(null)
            },
            onOpen = {
                val pkg = app.downloadUrl?.substringAfterLast("/")?.substringBefore("?") ?: ""
                viewModel.launchInstalledPackage(pkg)
            },
            onDismiss = {
                viewModel.selectAppForDetail(null)
            }
        )
    }

    // Install Dialog (APK & XAPK)
    pendingInstallModel?.let { model ->
        InstallDialog(
            model = model,
            isInstalling = isInstalling,
            installProgress = installProgress,
            installMessage = installMessage,
            autoLaunchEnabled = autoLaunch,
            onConfirmInstall = {
                viewModel.executeInstall(model)
            },
            onDismiss = {
                viewModel.dismissInstallDialog()
            }
        )
    }
}

@Composable
fun CosmoTopBar(currentTab: CosmoTab) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cosmo_top_bar"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_cosmo_store_icon),
                contentDescription = "Cosmo Game Store",
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COSMO GAME STORE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Text(
                    text = currentTab.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            FlatBadge(
                text = "ONLINE",
                backgroundColor = FlatAccentGreen.copy(alpha = 0.2f),
                textColor = FlatAccentGreen
            )
        }
    }
}

@Composable
fun CosmoBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_nav_bar"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            CosmoTab.values().forEachIndexed { index, tab ->
                val isSelected = selectedTabIndex == index
                BottomBarItem(
                    tab = tab,
                    isSelected = isSelected,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun BottomBarItem(
    tab: CosmoTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
            .testTag(tab.tag),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            0.5.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tab.title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
