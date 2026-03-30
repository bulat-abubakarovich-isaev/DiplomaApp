package com.example.anonymousmeetup.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.ProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onFriendsClick: () -> Unit,
    onEncountersClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val nickname by viewModel.nickname.collectAsState(initial = "")
    val publicKey by viewModel.publicKey.collectAsState()
    val keyDate by viewModel.keyDate.collectAsState()
    val isTrackingEnabled by viewModel.isLocationTrackingEnabled.collectAsState(initial = false)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val secureBackupEnabled by viewModel.secureBackupEnabled.collectAsState(initial = false)
    val backupPayload by viewModel.backupPayload.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }
    var editedNickname by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var importData by remember { mutableStateOf("") }

    val timeLeft by produceState(initialValue = "—") {
        while (true) {
            value = timeUntilNextUtc()
            delay(60_000)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            startTracking(context)
            viewModel.setLocationTrackingEnabled(true)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Профиль") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        ScreenBackground(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = nickname?.ifBlank { "Аноним" } ?: "Аноним",
                                style = MaterialTheme.typography.titleLarge
                            )
                            TextButton(onClick = {
                                editedNickname = nickname ?: ""
                                showEditDialog = true
                            }) {
                                Text("Изменить")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ваш профиль для встреч по интересам",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ваш ключ сегодня",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = publicKey ?: "—", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Дата (UTC): ${keyDate ?: "—"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Обновится через $timeLeft",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val key = publicKey ?: return@Button
                                clipboardManager.setText(AnnotatedString("Публичный ключ: $key"))
                                scope.launch { snackbarHostState.showSnackbar("Ключ скопирован") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Скопировать ключ")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedActionButton(
                                text = "Экспорт",
                                icon = Icons.Default.UploadFile,
                                modifier = Modifier.weight(1f)
                            ) { showExportDialog = true }
                            OutlinedActionButton(
                                text = "Импорт",
                                icon = Icons.Default.Key,
                                modifier = Modifier.weight(1f)
                            ) { showImportDialog = true }
                        }
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SwitchRow(
                            icon = Icons.Default.Notifications,
                            title = "Уведомления",
                            subtitle = "Уведомления о событиях в приложении",
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setNotificationsEnabled(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasNotifPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasNotifPermission) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )

                        SwitchRow(
                            icon = Icons.Default.LocationOn,
                            title = "Геолокация и proximity",
                            subtitle = "Радиус уведомлений 50 м",
                            checked = isTrackingEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasPermission) {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else {
                                        startTracking(context)
                                        viewModel.setLocationTrackingEnabled(true)
                                    }
                                } else {
                                    stopTracking(context)
                                    viewModel.setLocationTrackingEnabled(false)
                                }
                            }
                        )

                        SwitchRow(
                            icon = Icons.Default.Security,
                            title = "Secure backup",
                            subtitle = "Включает экспорт/импорт ключей",
                            checked = secureBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setSecureBackupEnabled(enabled)
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Разрешение геолокации", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (hasLocationPermission) "Разрешено" else "Не разрешено",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            TextButton(onClick = { openAppSettings(context) }) {
                                Text("Настройки")
                            }
                        }

                        ProfileActionRow(icon = Icons.Default.People, text = "Друзья", onClick = onFriendsClick)
                        ProfileActionRow(icon = Icons.Default.Place, text = "Встреченные", onClick = onEncountersClick)
                        ProfileActionRow(icon = Icons.Default.LocationOn, text = "Мои группы", onClick = onGroupsClick)
                    }
                }

                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Выйти")
                }

                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    LaunchedEffect(backupPayload) {
        val payload = backupPayload ?: return@LaunchedEffect
        clipboardManager.setText(AnnotatedString(payload))
        snackbarHostState.showSnackbar("Backup скопирован в буфер")
        viewModel.consumeBackupPayload()
    }

    LaunchedEffect(info) {
        info?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Изменить ник") },
            text = {
                OutlinedTextField(
                    value = editedNickname,
                    onValueChange = { editedNickname = it },
                    label = { Text("Новый ник") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editedNickname.isNotBlank()) {
                        viewModel.updateNickname(editedNickname.trim())
                        showEditDialog = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт ключей") },
            text = {
                OutlinedTextField(
                    value = exportPassword,
                    onValueChange = { exportPassword = it },
                    label = { Text("Пароль для backup") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (exportPassword.isNotBlank()) {
                        viewModel.exportBackup(exportPassword)
                        exportPassword = ""
                        showExportDialog = false
                    }
                }) { Text("Экспорт") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт ключей") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importData,
                        onValueChange = { importData = it },
                        label = { Text("Backup JSON") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (importData.isNotBlank() && importPassword.isNotBlank()) {
                        viewModel.importBackup(importData, importPassword)
                        importData = ""
                        importPassword = ""
                        showImportDialog = false
                    }
                }) { Text("Импорт") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OutlinedActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text)
    }
}

private fun timeUntilNextUtc(): String {
    val tz = TimeZone.getTimeZone("UTC")
    val now = Calendar.getInstance(tz)
    val next = Calendar.getInstance(tz).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DATE, 1)
    }
    val diffMillis = next.timeInMillis - now.timeInMillis
    val hours = diffMillis / (60 * 60 * 1000)
    val minutes = (diffMillis / (60 * 1000)) % 60
    return String.format("%02d:%02d", hours, minutes)
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(intent)
}

private fun startTracking(context: android.content.Context) {
    // Global proximity tracker removed in anonymous architecture.
}

private fun stopTracking(context: android.content.Context) {
    // Global proximity tracker removed in anonymous architecture.
}


