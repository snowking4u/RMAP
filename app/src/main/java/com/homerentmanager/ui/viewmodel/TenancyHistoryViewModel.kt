package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.data.model.TenancyWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class TenancyFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    ENDED("Ended"),
}

data class TenancyHistoryUiState(
    val loading: Boolean = true,
    val tenancies: List<TenancyWithDetails> = emptyList(),
    val filter: TenancyFilter = TenancyFilter.ALL,
    val query: String = "",
    val activeCount: Int = 0,
)

class TenancyHistoryViewModel(container: AppContainer) : HrmViewModel() {
    private val filter = MutableStateFlow(TenancyFilter.ALL)
    private val query = MutableStateFlow("")

    val state = combine(
        container.tenancyRepository.observeAll(),
        filter,
        query,
    ) { tenancies, selectedFilter, search ->
        val filtered = tenancies.filter { detail ->
            val statusMatch = when (selectedFilter) {
                TenancyFilter.ALL -> true
                TenancyFilter.ACTIVE -> detail.tenancy.status.name == "ACTIVE"
                TenancyFilter.ENDED -> detail.tenancy.status.name == "ENDED"
            }
            val queryMatch = search.isBlank() ||
                detail.tenant.name.contains(search, ignoreCase = true) ||
                detail.room.roomNumber.contains(search, ignoreCase = true)
            statusMatch && queryMatch
        }
        TenancyHistoryUiState(
            loading = false,
            tenancies = filtered,
            filter = selectedFilter,
            query = search,
            activeCount = tenancies.count { it.tenancy.status.name == "ACTIVE" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TenancyHistoryUiState())

    fun setFilter(value: TenancyFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }
}
