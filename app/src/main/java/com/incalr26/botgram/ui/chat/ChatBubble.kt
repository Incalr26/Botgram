package com.incalr26.botgram.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MessageReaction(val emoji: String, val userId: String)

@Composable
fun ChatBubble(
    senderName: String,
    messageText: String,
    isAdmin: Boolean = false,
    isOwner: Boolean = false,
    customTag: String? = null,
    quotedMessage: String? = null,
    isPlaceholder: Boolean = false,
    placeholderSizeText: String = "",
    reactions: List<MessageReaction> = emptyList(),
    onReactionToggle: (String) -> Unit,
    onMenuOpen: () -> Unit,
    onPreviewOpen: () -> Unit
) {
    var isSelectionActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .pointerInput(isSelectionActive) {
                    detectTapGestures(
                        onLongPress = {
                            isSelectionActive = true
                            onMenuOpen()
                        },
                        onTap = {
                            if (isSelectionActive) {
                                isSelectionActive = false
                                focusManager.clearFocus()
                            } else {
                                onMenuOpen()
                            }
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = senderName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )

                    if (isOwner) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IdentityTag(
                            text = "所有者",
                            backgroundColor = MaterialTheme.colorScheme.tertiary,
                            textColor = MaterialTheme.colorScheme.onTertiary
                        )
                    } else if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IdentityTag(
                            text = "管理员",
                            backgroundColor = MaterialTheme.colorScheme.secondary,
                            textColor = MaterialTheme.colorScheme.onSecondary
                        )
                    }

                    if (!customTag.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IdentityTag(
                            text = customTag,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!quotedMessage.isNullOrEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = quotedMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (isPlaceholder) {
                    PlaceholderBox(
                        sizeText = placeholderSizeText,
                        onClick = onPreviewOpen
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = messageText,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.pointerInput(isSelectionActive) {
                                detectTapGestures(
                                    onTap = {
                                        if (isSelectionActive) {
                                            isSelectionActive = false
                                            focusManager.clearFocus()
                                        } else {
                                            onMenuOpen()
                                        }
                                    },
                                    onLongPress = {
                                        isSelectionActive = true
                                        onMenuOpen()
                                    }
                                )
                            }
                        )
                    }
                }

                if (reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ReactionRow(reactions = reactions, onToggle = onReactionToggle)
                }
            }
        }
    }
}

@Composable
fun IdentityTag(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun PlaceholderBox(sizeText: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "预览下载",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sizeText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReactionRow(reactions: List<MessageReaction>, onToggle: (String) -> Unit) {
    val groupedReactions = reactions.groupBy { it.emoji }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        groupedReactions.forEach { (emoji, list) ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onToggle(emoji) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = list.size.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
