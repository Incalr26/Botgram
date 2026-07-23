package com.incalr26.botgram.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatName: String,
    onBack: () -> Unit,
    onSharedPreviewOpen: () -> Unit
) {
    val systemUiController = rememberSystemUiController()
    val statusBarColor = MaterialTheme.colorScheme.surface
    
    LaunchedEffect(statusBarColor) {
        systemUiController.setStatusBarColor(
            color = statusBarColor,
            darkIcons = true
        )
    }

    var isAvatarExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isAvatarExpanded) {
                        Text(text = chatName, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(text = chatName, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 头像区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isAvatarExpanded) 24.dp else 8.dp)
                    .clickable { isAvatarExpanded = !isAvatarExpanded }
                    .pointerInput(isAvatarExpanded) {
                        if (isAvatarExpanded) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (dragAmount.y > 50f) {
                                    onSharedPreviewOpen()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isAvatarExpanded) 200.dp else 80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 详情列表区域
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(5) { index ->
                    ListItem(
                        headlineContent = { Text("设置项 $index", color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier.clickable { /* 点击波纹效果 */ },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}
