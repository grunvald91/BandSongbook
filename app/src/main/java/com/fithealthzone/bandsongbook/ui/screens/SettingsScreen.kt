package com.fithealthzone.bandsongbook.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fithealthzone.bandsongbook.data.settings.ThemeMode
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.GlassCard
import com.fithealthzone.bandsongbook.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val display by vm.displaySettings.collectAsState()
    val context = LocalContext.current

    var memberName by remember { mutableStateOf("") }
    var themeMode by remember { mutableStateOf(ThemeMode.DARK) }
    var lyricsFontSlider by remember { mutableFloatStateOf(16f) }
    var chordsFontSlider by remember { mutableFloatStateOf(14f) }

    val primaryButtonColors = ButtonDefaults.buttonColors(
        containerColor = AppColors.Primary,
        contentColor = Color.White
    )

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { raw ->
            if (!raw.isNullOrBlank()) vm.importBackupJson(raw)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        vm.exportBackupJson(memberName) { raw ->
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) }
            }
        }
    }

    LaunchedEffect(display) {
        themeMode = display.themeMode
        lyricsFontSlider = display.lyricsFontSp.toFloat()
        chordsFontSlider = display.chordsFontSp.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("НАСТРОЙКИ", color = AppColors.TextWhite, fontSize = 34.sp, fontWeight = FontWeight.Black)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ВНЕШНИЙ ВИД", color = AppColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                SettingRowLabel("Тема") {
                    SegmentedChoice(
                        left = "Тёмная",
                        right = "Светлая",
                        leftSelected = themeMode == ThemeMode.DARK,
                        onLeft = {
                            themeMode = ThemeMode.DARK
                            vm.saveDisplaySettings(display.preferFlats, themeMode, lyricsFontSlider.toInt(), chordsFontSlider.toInt())
                        },
                        onRight = {
                            themeMode = ThemeMode.LIGHT
                            vm.saveDisplaySettings(display.preferFlats, themeMode, lyricsFontSlider.toInt(), chordsFontSlider.toInt())
                        }
                    )
                }

                SettingSlider(
                    title = "Размер текста",
                    value = lyricsFontSlider,
                    range = 12f..30f,
                    onChange = { lyricsFontSlider = it },
                    onFinished = {
                        vm.saveDisplaySettings(display.preferFlats, themeMode, lyricsFontSlider.toInt(), chordsFontSlider.toInt())
                    }
                )

                SettingSlider(
                    title = "Размер аккордов",
                    value = chordsFontSlider,
                    range = 10f..28f,
                    onChange = { chordsFontSlider = it },
                    onFinished = {
                        vm.saveDisplaySettings(display.preferFlats, themeMode, lyricsFontSlider.toInt(), chordsFontSlider.toInt())
                    }
                )

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Превью", color = AppColors.TextMuted, fontSize = 12.sp)
                        Text(
                            "A♯ / B♭   D♯ / E♭   F",
                            color = AppColors.PrimaryLight,
                            fontSize = chordsFontSlider.toInt().sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Снова в путь — мы поём вместе",
                            color = AppColors.TextLight,
                            fontSize = lyricsFontSlider.toInt().sp
                        )
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ПРОФИЛЬ И СИНХРОНИЗАЦИЯ", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Имя участника, группа, QR и синхронизация теперь находятся во вкладке Профиль.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("РЕЗЕРВНАЯ КОПИЯ", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("bandsongbook-backup.json") }, colors = primaryButtonColors) {
                        Text("Экспорт JSON")
                    }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, colors = primaryButtonColors) {
                        Text("Импорт JSON")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRowLabel(title: String, right: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = AppColors.TextLight, fontWeight = FontWeight.Medium)
        right()
    }
}

@Composable
private fun SegmentedChoice(
    left: String,
    right: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(220.dp)
    ) {
        SegmentButton(
            text = left,
            selected = leftSelected,
            onClick = onLeft,
            modifier = Modifier.weight(1f)
        )
        SegmentButton(
            text = right,
            selected = !leftSelected,
            onClick = onRight,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AppColors.Primary else AppColors.BgCardHover,
            contentColor = if (selected) Color.White else AppColors.TextMuted
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.padding(horizontal = 2.dp).height(36.dp)
    ) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = AppColors.TextLight, fontWeight = FontWeight.Medium)
            Text(value.toInt().toString(), color = AppColors.TextMuted)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = onFinished
        )
    }
}
