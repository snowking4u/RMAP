package com.homerentmanager.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SearchBox
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.TenancyFilter
import com.homerentmanager.ui.viewmodel.TenancyHistoryViewModel
import com.homerentmanager.util.formatDisplayOrDash
import com.homerentmanager.util.label
import com.homerentmanager.util.toRupeesNoDecimals
import java.util.UUID

@Composable
fun TenancyHistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTenant: (UUID) -> Unit,
    onOpenRoom: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel { TenancyHistoryViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    HrmScaffold(
        title = "Tenancy history",
        subtitle = "${state.activeCount} active · ${state.tenancies.size} shown",
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = hrmPagePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SearchBox(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Search tenant or room",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TenancyFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
            if (state.tenancies.isEmpty()) {
                item {
                    EmptyState(
                        icon = HrmIcons.History,
                        title = "No tenancy records",
                        message = "Start a tenancy from a tenant profile to build history here.",
                    )
                }
            } else {
                items(state.tenancies, key = { it.tenancy.id }) { detail ->
                    val active = detail.tenancy.status.name == "ACTIVE"
                    Card(
                        onClick = { onOpenTenant(detail.tenant.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        detail.tenant.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        detail.tenant.mobile.ifBlank { "No mobile number" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Tag(
                                    text = detail.tenancy.status.label(),
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    container = if (active) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${detail.tenancy.joinDate.formatDisplayOrDash()} – " +
                                    "${detail.tenancy.leaveDate.formatDisplayOrDash()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onOpenRoom(detail.room.id) }) {
                                    Text("Room ${detail.room.roomNumber}")
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    detail.tenancy.monthlyRentPaise.toRupeesNoDecimals(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (detail.tenancy.securityDepositPaise > 0) {
                                Text(
                                    "Deposit " +
                                        detail.tenancy.securityDepositPaise.toRupeesNoDecimals(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
