package com.freeup.textactions.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.freeup.textactions.R
import com.freeup.textactions.ui.theme.LightBackground
import com.freeup.textactions.ui.theme.LightOnSurface
import com.freeup.textactions.ui.theme.LightSurface
import com.freeup.textactions.ui.theme.DarkOnSurface
import com.freeup.textactions.ui.theme.DarkSurface
import com.freeup.textactions.ui.theme.DarkSurfaceContainerHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryActionSheet(
    title: String,
    onDismiss: () -> Unit,
    onRerun: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val sheetColor = if (isDark) DarkSurface else LightSurface
    val rowColor = if (isDark) DarkSurfaceContainerHigh else LightBackground
    val contentColor = if (isDark) DarkOnSurface else LightOnSurface
    val destructiveColor = if (isDark) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            SheetActionRow(
                icon = AppIcons.Refresh,
                label = stringResource(R.string.rerun_action),
                onClick = onRerun,
                containerColor = rowColor,
                contentColor = contentColor
            )
            SheetActionRow(
                icon = AppIcons.Copy,
                label = stringResource(R.string.copy_text),
                onClick = onCopy,
                containerColor = rowColor,
                contentColor = contentColor
            )
            SheetActionRow(
                icon = AppIcons.Share,
                label = stringResource(R.string.share_text),
                onClick = onShare,
                containerColor = rowColor,
                contentColor = contentColor
            )
            SheetActionRow(
                icon = AppIcons.Trash,
                label = stringResource(R.string.delete_history),
                onClick = onDelete,
                isDestructive = true,
                containerColor = rowColor,
                contentColor = destructiveColor
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
    }
}
