package com.promoapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.repository.DealRepository
import com.promoapp.domain.usecase.GetFilterOptionsUseCase
import com.promoapp.domain.usecase.SearchDealsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        searchDeals: SearchDealsUseCase,
        getFilterOptions: GetFilterOptionsUseCase,
        private val repository: DealRepository,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        /** Filtros aplicados. O texto da busca fica separado em [query] (com debounce). */
        private val _filters = MutableStateFlow(SearchFilters())
        val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

        private val _options = MutableStateFlow<FilterOptions?>(null)
        val options: StateFlow<FilterOptions?> = _options.asStateFlow()

        @OptIn(FlowPreview::class)
        private val criteria: Flow<SearchFilters> =
            combine(
                _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
                _filters,
            ) { text, filters -> filters.copy(query = text.trim()) }
                .distinctUntilChanged()

        val hasCriteria: StateFlow<Boolean> =
            criteria
                .map { it.hasCriteria }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        @OptIn(ExperimentalCoroutinesApi::class)
        val results: Flow<PagingData<Deal>> =
            criteria
                .flatMapLatest { if (it.hasCriteria) searchDeals(it) else flowOf(PagingData.empty()) }
                .cachedIn(viewModelScope)

        val favoriteIds: StateFlow<Set<Long>> =
            repository
                .observeFavoriteIds()
                .map { it.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        init {
            viewModelScope.launch { _options.value = getFilterOptions().getOrNull() }
        }

        fun onQueryChange(text: String) {
            _query.value = text
        }

        fun onFiltersApplied(filters: SearchFilters) {
            _filters.value = filters.copy(query = "")
        }

        fun onToggleFavorite(dealId: Long) {
            viewModelScope.launch {
                repository.setFavorite(dealId, favorite = dealId !in favoriteIds.value)
            }
        }

        private companion object {
            const val DEBOUNCE_MS = 400L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
