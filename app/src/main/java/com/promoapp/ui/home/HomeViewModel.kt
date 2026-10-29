package com.promoapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.DealTab
import com.promoapp.domain.repository.DealRepository
import com.promoapp.domain.usecase.GetDealsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getDeals: GetDealsUseCase,
    private val repository: DealRepository,
) : ViewModel() {

    private val _tab = MutableStateFlow(DealTab.FOR_YOU)
    val tab: StateFlow<DealTab> = _tab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val deals: Flow<PagingData<Deal>> = _tab
        .flatMapLatest { getDeals(it) }
        .cachedIn(viewModelScope)

    val favoriteIds: StateFlow<Set<Long>> = repository.observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun onTabSelected(tab: DealTab) {
        _tab.value = tab
    }

    fun onToggleFavorite(dealId: Long) {
        viewModelScope.launch {
            repository.setFavorite(dealId, favorite = dealId !in favoriteIds.value)
        }
    }
}
