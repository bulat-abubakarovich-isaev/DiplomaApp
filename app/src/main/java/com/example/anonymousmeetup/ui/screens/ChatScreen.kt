package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.ui.components.LongPressButton
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit,
    onOpenPrivateChat: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val error by viewModel.error.collectAsState()

    var showPrivateDialog by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var recipientAlias by remember { mutableStateOf("") }
    var recipientPublicKey by remember { mutableStateOf("") }

    LaunchedEffect(groupId) {
        viewModel.loadMessages(groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чат группы") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showPrivateDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Приватный чат")
                    }
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Default.Map, contentDescription = "Карта")
                    }
                }
            )
        }
    ) { paddingValues ->
        ScreenBackground(modifier = Modifier.padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пока нет сообщений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        items(messages) { message ->
                            MessageItem(message)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Введите сообщение") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LongPressButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendMessage(groupId, messageText)
                                    messageText = ""
                                }
                            },
                            onLongPress = {
                                showPrivateDialog = true
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }

            if (showPrivateDialog) {
                AlertDialog(
                    onDismissRequest = { showPrivateDialog = false },
                    title = { Text("Начать приватный чат") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = recipientAlias,
                                onValueChange = { recipientAlias = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Псевдоним собеседника") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = recipientPublicKey,
                                onValueChange = { recipientPublicKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Публичный ключ") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val alias = recipientAlias.ifBlank { "Собеседник" }
                                if (recipientPublicKey.isBlank()) {
                                    viewModel.setError("Введите публичный ключ")
                                    return@Button
                                }

                                viewModel.startPrivateChat(alias, recipientPublicKey) { sessionId ->
                                    if (sessionId != null) {
                                        showPrivateDialog = false
                                        recipientAlias = ""
                                        recipientPublicKey = ""
                                        onOpenPrivateChat(sessionId)
                                    }
                                }
                            }
                        ) {
                            Text("Открыть")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPrivateDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(errorMessage)
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: ChatViewModel.UiMessage) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isMine = message.isMine
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bubbleShape: Shape = if (isMine) {
        RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
    } else {
        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
    }
    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Группа",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = message.senderAlias,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = dateFormat.format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(color = bubbleColor, shape = bubbleShape) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
