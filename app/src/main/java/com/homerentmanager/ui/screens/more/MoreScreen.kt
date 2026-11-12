package com.homerentmanager.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homerentmanager.ui.Routes
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.InfoBanner
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.hrmPagePadding

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    HrmScaffold(
        title = "More",
        subtitle = "History, reports and data safety",
        message = null,
        onMessageShown = {},
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = hrmPagePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionHeader(title = "Manage")
            }
            item {
                MoreRow(
                    icon = HrmIcons.History,
                    title = "Tenancy history",
                    subtitle = "Every past and active tenancy with dates and rent",
                    onClick = { onNavigate(Routes.HISTORY) },
                )
            }
            item {
                MoreRow(
                    icon = HrmIcons.Electricity,
                    title = "Electricity",
                    subtitle = "Meter readings, units and consumption charges",
                    onClick = { onNavigate(Routes.ELECTRICITY) },
                )
            }
            item {
                MoreRow(
                    icon = HrmIcons.Payments,
                    title = "Payments",
                    subtitle = "All receipts, partial payments and PDF receipts",
                    onClick = { onNavigate(Routes.PAYMENTS) },
                )
            }
            item {
                MoreRow(
                    icon = HrmIcons.Reports,
                    title = "Reports",
                    subtitle = "Monthly performance, ageing, methods and CSV export",
                    onClick = { onNavigate(Routes.REPORTS) },
                )
            }
            item {
                MoreRow(
                    icon = HrmIcons.Backup,
                    title = "Backup & restore",
                    subtitle = "Encrypted manual backups, validation and Auto Backup status",
                    onClick = { onNavigate(Routes.BACKUP) },
                )
            }
            item {
                MoreRow(
                    icon = HrmIcons.Settings,
                    title = "Settings",
                    subtitle = "Property details, billing defaults and reminders",
                    onClick = { onNavigate(Routes.SETTINGS) },
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                InfoBanner(
                    title = "Offline by design",
                    text = "All rental data is stored in a private SQLite database on this device. " +
                        "There is no login, server, cloud database or subscription. Use encrypted " +
                        "manual backups to protect against loss or device replacement.",
                )
            }
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
