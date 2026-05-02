package com.fithealthzone.bandsongbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.StageIconButton
import com.fithealthzone.bandsongbook.ui.viewmodel.SetlistsViewModel

@Composable
fun SetlistsScreen(onOpenSetlist: (String) -> Unit) {
    val vm: SetlistsViewModel = viewModel()
    val setlists by vm.setlists.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pendingDeleteSetlistId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (!showCreateSheet) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    containerColor = AppColors.Primary,
                    contentColor = if (AppColors.isDark) AppColors.BgDeep else Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Создать сетлист")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(AppColors.BgCard, RoundedCornerShape(24.dp))
                            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("СЕТЛИСТЫ", color = AppColors.TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("${setlists.size} наборов", color = AppColors.TextMuted, fontSize = 12.sp)
                    }
                }

                if (setlists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.BgCard, RoundedCornerShape(20.dp))
                                .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = AppColors.TextDim,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "Нет сетлистов",
                                    color = AppColors.TextMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Нажми +, чтобы создать первый",
                                    color = AppColors.TextDim,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(setlists, key = { _, it -> it.id }) { index, setlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.BgCard, RoundedCornerShape(20.dp))
                                .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(20.dp))
                                .clickable { onOpenSetlist(setlist.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(AppColors.BgSurface, RoundedCornerShape(12.dp))
                                    .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = if (index == 0) "Основной сетлист" else "Сетлист",
                                    tint = AppColors.PrimaryLight
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = setlist.name,
                                    color = AppColors.TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Обновлён: ${formatSetlistUpdatedAt(setlist.updatedAt)}",
                                    color = AppColors.TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            SetlistIconActionButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Удалить сетлист ${setlist.name}",
                                tint = AppColors.Error,
                                onClick = { pendingDeleteSetlistId = setlist.id }
                            )
                        }
                    }
                }
            }

            val setlistToDelete = setlists.firstOrNull { it.id == pendingDeleteSetlistId }
            if (setlistToDelete != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteSetlistId = null },
                    title = { Text("Удалить сетлист") },
                    text = { Text("Удалить «${setlistToDelete.name}»? Пункты сетлиста тоже будут скрыты после синхронизации.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.delete(setlistToDelete)
                            pendingDeleteSetlistId = null
                        }) {
                            Text("Удалить", color = AppColors.Error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteSetlistId = null }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            if (showCreateSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            name = ""
                            showCreateSheet = false
                        }
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .background(AppColors.BgDeep, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(
                            width = 1.dp,
                            color = AppColors.BorderGlassStrong,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Новый сетлист", color = AppColors.TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        SetlistIconActionButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = AppColors.TextMuted,
                            onClick = {
                                name = ""
                                showCreateSheet = false
                            }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Название", color = AppColors.TextDim) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextLight,
                            unfocusedTextColor = AppColors.TextLight,
                            focusedPlaceholderColor = AppColors.TextDim,
                            unfocusedPlaceholderColor = AppColors.TextDim,
                            focusedBorderColor = AppColors.PrimaryLight,
                            unfocusedBorderColor = AppColors.BorderGlassStrong,
                            focusedContainerColor = AppColors.BgCard,
                            unfocusedContainerColor = AppColors.BgCard,
                            cursorColor = AppColors.PrimaryLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SetlistIconActionButton(
                            icon = Icons.Default.Check,
                            contentDescription = if (name.isNotBlank()) "Создать сетлист" else "Введите название сетлиста",
                            active = name.isNotBlank(),
                            enabled = name.isNotBlank(),
                            tint = if (name.isNotBlank()) {
                                if (AppColors.isDark) AppColors.BgDeep else Color.White
                            } else {
                                AppColors.TextDim
                            }
                        ) {
                            vm.create(name)
                            name = ""
                            showCreateSheet = false
                        }
                    }
                }
            }
        }
    }
}

private fun formatSetlistUpdatedAt(epochMs: Long): String {
    return runCatching {
        java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }.getOrElse { epochMs.toString() }
}

@Composable
private fun SetlistIconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        active = active,
        enabled = enabled,
        tint = tint,
        backgroundColor = AppColors.BgSurface,
        borderColor = AppColors.BorderGlass,
        onClick = onClick
    )
}
