package com.fithealthzone.bandsongbook.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StageIconButtonShape = RoundedCornerShape(12.dp)
private val PlayerHandleShape = RoundedCornerShape(999.dp)

private fun stageRippleColor(active: Boolean, enabled: Boolean): Color {
    return when {
        !enabled -> AppColors.BorderGlassStrong.copy(alpha = 0.22f)
        active -> AppColors.PrimaryLight.copy(alpha = 0.24f)
        else -> AppColors.Primary.copy(alpha = 0.18f)
    }
}

@Composable
fun StageIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    tint: Color = if (active) AppColors.PrimaryLight else AppColors.TextLight,
    backgroundColor: Color = AppColors.BgSurface.copy(alpha = 0.94f),
    activeBackgroundColor: Color = AppColors.Primary.copy(alpha = 0.18f),
    disabledBackgroundColor: Color = AppColors.BgSurface.copy(alpha = 0.55f),
    borderColor: Color = AppColors.BorderGlass.copy(alpha = 0.38f),
    activeBorderColor: Color = AppColors.PrimaryLight,
    disabledTint: Color = AppColors.TextDim,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                if (active) selected = true
            }
            .size(buttonSize)
            .background(
                when {
                    !enabled -> disabledBackgroundColor
                    active -> activeBackgroundColor
                    else -> backgroundColor
                },
                StageIconButtonShape
            )
            .border(
                1.dp,
                if (active && enabled) activeBorderColor else borderColor,
                StageIconButtonShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = true,
                    color = stageRippleColor(active = active, enabled = enabled),
                    radius = buttonSize * 0.72f
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else disabledTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun StageTextToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                if (active) selected = true
            }
            .size(buttonSize)
            .background(
                if (active) AppColors.Primary.copy(alpha = 0.18f) else AppColors.BgSurface,
                StageIconButtonShape
            )
            .border(
                1.dp,
                if (active) AppColors.PrimaryLight else AppColors.BorderGlass,
                StageIconButtonShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = true,
                    color = stageRippleColor(active = active, enabled = true),
                    radius = buttonSize * 0.72f
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) AppColors.PrimaryLight else AppColors.TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PlayerTransportButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        active = active || prominent,
        enabled = enabled,
        buttonSize = if (prominent) 56.dp else 44.dp,
        iconSize = if (prominent) 28.dp else 20.dp,
        tint = if (prominent) Color.White else if (active) AppColors.PrimaryLight else AppColors.TextLight,
        backgroundColor = AppColors.BgSurface.copy(alpha = 0.96f),
        activeBackgroundColor = if (prominent) AppColors.Primary else AppColors.Primary.copy(alpha = 0.18f),
        borderColor = AppColors.BorderGlassStrong,
        activeBorderColor = AppColors.PrimaryLight,
        onClick = onClick
    )
}

@Composable
fun PlayerSheetHandle(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = true,
                    color = AppColors.PrimaryLight.copy(alpha = 0.18f),
                    radius = 52.dp
                ),
                onClick = onClick
            )
            .background(AppColors.BgSurface.copy(alpha = 0.94f), PlayerHandleShape)
            .border(1.dp, AppColors.BorderGlassStrong, PlayerHandleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (expanded) "▾  $label" else "▴  $label",
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(110.dp)
        )
    }
}

@Composable
fun PlayerDragHandle(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = true,
                    color = AppColors.PrimaryLight.copy(alpha = 0.18f),
                    radius = 64.dp
                ),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 4.dp)
                .background(
                    color = if (expanded) AppColors.PrimaryLight.copy(alpha = 0.9f) else AppColors.TextDim.copy(alpha = 0.78f),
                    shape = PlayerHandleShape
                )
        )
    }
}
