package com.promoapp.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.promoapp.domain.model.DealDetail
import com.promoapp.domain.model.PriceAlert
import com.promoapp.domain.repository.AlertRepository
import com.promoapp.domain.usecase.GetDealDetailUseCase
import com.promoapp.ui.navigation.DealDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DealDetailUiState {
    data object Loading : DealDetailUiState

    data object Error : DealDetailUiState

    data class Success(
        val detail: DealDetail,
    ) : DealDetailUiState
}

@HiltViewModel
class DealDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getDealDetail: GetDealDetailUseCase,
        private val alerts: AlertRepository,
    ) : ViewModel() {
        private val dealId = savedStateHandle.toRoute<DealDetailRoute>().dealId

        private val _state = MutableStateFlow<DealDetailUiState>(DealDetailUiState.Loading)
        val state: StateFlow<DealDetailUiState> = _state.asStateFlow()

        val alert: StateFlow<PriceAlert?> =
            alerts
                .observeAlert(dealId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        init {
            load()
        }

        fun load() {
            _state.value = DealDetailUiState.Loading
            viewModelScope.launch {
                _state.value =
                    getDealDetail(dealId).fold(
                        onSuccess = { DealDetailUiState.Success(it) },
                        onFailure = { DealDetailUiState.Error },
                    )
            }
        }

        fun onCreateAlert(targetPrice: Double) {
            val deal = (_state.value as? DealDetailUiState.Success)?.detail?.deal ?: return
            viewModelScope.launch { alerts.createAlert(deal, targetPrice) }
        }

        fun onRemoveAlert() {
            viewModelScope.launch { alerts.deleteAlert(dealId) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
