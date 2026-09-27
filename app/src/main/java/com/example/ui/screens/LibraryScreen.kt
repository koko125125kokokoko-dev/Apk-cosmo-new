package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AppDatabase
import com.example.data.db.DownloadedGameEntity
import com.example.data.model.InstalledAppInfo
import com.example.installer.ApkParser
import com.example.installer.PackageModel
import com.example.installer.PackageType
import com.example.installer.XapkParser
import com.example.notification.DownloadNotificationHelper
import com.example.ui.components.PackageTypeBadge
import com.example.ui.theme.FlatAccentGreen
import com.example.ui.theme.FlatAccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun LibraryScreen(
    downloadedEntities: List<DownloadedGameEntity>,
    onInstallPackageModel: (PackageModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Downloaded Packages, 1: Installed Apps

    var localPackages by remember { mutableStateOf<List<PackageModel>>(emptyList()) }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // SAF file picker launcher to pick APK or XAPK from storage
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    Toast.makeText(context, "Reading selected package file...", Toast.LENGTH_SHORT).show()
                    val targetFile = copyUriToTemp(context, uri)
                    if (targetFile != null && targetFile.exists()) {
                        val isXapk = targetFile.name.endsWith(".xapk", ignoreCase = true)
                        val model = if (isXapk) {
                            XapkParser.parse(context, targetFile)
                        } else {
                            ApkParser.parse(context, targetFile)
                        }
                        if (model != null) {
                            onInstallPackageModel(model)
                        } else {
                            Toast.makeText(context, "Could not parse package file", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Scan storage and database packages
    fun refreshPackages() {
        scope.launch {
            isScanning = true
            val models = withContext(Dispatchers.IO) {
                val list = mutableListOf<PackageModel>()
                val seenPaths = mutableSetOf<String>()

                // 1. Load from Room DB
                for (entity in downloadedEntities) {
                    val file = File(entity.filePath)
                    if (file.exists() && seenPaths.add(file.absolutePath)) {
                        val model = if (entity.isXapk) {
                            XapkParser.parse(context, file)
                        } else {
                            ApkParser.parse(context, file)
                        }
                        if (model != null) list.add(model)
                    }
                }

                // 2. Scan Downloads directory
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() && downloadsDir.isDirectory) {
                    val files = downloadsDir.listFiles { f ->
                        f.isFile && (f.name.endsWith(".apk", ignoreCase = true) || f.name.endsWith(".xapk", ignoreCase = true))
                    }
                    if (files != null) {
                        for (file in files) {
                            if (seenPaths.add(file.absolutePath)) {
                                val isXapk = file.name.endsWith(".xapk", ignoreCase = true)
                                val model = if (isXapk) {
                                    XapkParser.parse(context, file)
                                } else {
                                    ApkParser.parse(context, file)
                                }
                                if (model != null) list.add(model)
                            }
                        }
                    }
                }
                list.sortedByDescending { it.file.lastModified() }
            }
            localPackages = models
            isScanning = false
        }
    }

    // Scan installed applications
    fun refreshInstalledApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(0)
                packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!isSystem && pkg.packageName != context.packageName) {
                        InstalledAppInfo(
                            name = appInfo.loadLabel(pm).toString(),
                            packageName = pkg.packageName,
                            versionName = pkg.versionName ?: "1.0",
                            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                pkg.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                pkg.versionCode.toLong()
                            },
                            isSystemApp = false
                        )
                    } else null
                }.sortedBy { it.name.lowercase() }
            }
            installedApps = apps
        }
    }

    LaunchedEffect(downloadedEntities) {
        refreshPackages()
        refreshInstalledApps()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_library")
    ) {
        // Sub-Navigation Bar (Flat Tabs)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    LibrarySubTabItem(
                        title = "DOWNLOADED PACKAGES (${localPackages.size})",
                        isSelected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    LibrarySubTabItem(
                        title = "INSTALLED APPS (${installedApps.size})",
                        isSelected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Action Bar (Pick File SAF & Search)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (selectedSubTab == 0) "Filter packages..." else "Filter installed apps...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("input_search_library")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (selectedSubTab == 0) {
                        Button(
                            onClick = {
                                safPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
                            },
                            shape = RoundedCornerShape(0.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("btn_saf_pick_file")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Pick File",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "PICK FILE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(
                            onClick = { refreshInstalledApps() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Apps",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Sub Tab Content
        if (selectedSubTab == 0) {
            val filteredPackages = remember(localPackages, searchQuery) {
                if (searchQuery.isBlank()) localPackages
                else localPackages.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredPackages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No packages available in storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Download games from Browse tab or pick local .apk / .xapk files using Pick File above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                safPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
                            },
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Select Package",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "SELECT APK OR XAPK FILE")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    items(filteredPackages, key = { it.file.absolutePath }) { model ->
                        DownloadedPackageItemRow(
                            model = model,
                            onInstall = { onInstallPackageModel(model) },
                            onDelete = {
                                scope.launch {
                                    try {
                                        if (model.file.exists()) model.file.delete()
                                        AppDatabase.getDatabase(context).gameDao().getAllPackages()
                                        refreshPackages()
                                        Toast.makeText(context, "Package file deleted", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            val filteredInstalled = remember(installedApps, searchQuery) {
                if (searchQuery.isBlank()) installedApps
                else installedApps.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredInstalled.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No non-system applications found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    items(filteredInstalled, key = { it.packageName }) { appInfo ->
                        InstalledAppItemRow(
                            app = appInfo,
                            onLaunch = {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                } else {
                                    Toast.makeText(context, "Cannot launch application", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onUninstall = {
                                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${appInfo.packageName}")
                                }
                                context.startActivity(uninstallIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySubTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
            .testTag(if (isSelected) "tab_sub_selected" else "tab_sub_unselected"),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            0.5.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun DownloadedPackageItemRow(
    model: PackageModel,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onInstall() }
            .testTag("package_item_${model.file.name}"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (model.iconBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = model.iconBitmap.asImageBitmap(),
                        contentDescription = model.title,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = "Package Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    PackageTypeBadge(isXapk = model.type == PackageType.XAPK)
                }

                Text(
                    text = "v${model.versionName} - ${DownloadNotificationHelper.formatBytes(model.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (model.isInstalled) {
                    Text(
                        text = "Installed (${model.installedVersionName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = FlatAccentGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_install_package_${model.file.name}")
            ) {
                Text(
                    text = if (model.isInstalled) "UPDATE" else "INSTALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_delete_package_${model.file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete File",
                    tint = FlatAccentRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun InstalledAppItemRow(
    app: InstalledAppInfo,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("installed_app_${app.packageName}"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = app.name,
                    tint = FlatAccentGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${app.versionName} (${app.packageName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onLaunch,
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "OPEN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedButton(
                onClick = onUninstall,
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(text = "REMOVE", fontSize = 10.sp, color = FlatAccentRed)
            }
        }
    }
}

suspend fun copyUriToTemp(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val fileName = getFileNameFromUri(context, uri) ?: "selected_package.apk"
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(65536)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
            }
        }
        tempFile
    } catch (_: Exception) {
        null
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1) name = it.getString(idx)
        }
    }
    return name
}
