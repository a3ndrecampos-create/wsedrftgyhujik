package com.promoapp.ui.coupons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promoapp.domain.model.Coupon
import com.promoapp.domain.model.sortedForDisplay
import com.promoapp.domain.usecase.GetCouponsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CouponsUiState {
    data object Loading : CouponsUiState

    data object Error : CouponsUiState

    data class Success(
        val coupons: List<Coupon>,
        val stores: List<String>,
        val selectedStore: String?,
    ) : CouponsUiState
}

@HiltViewModel
class CouponsViewModel
    @Inject
    constructor(
        private val getCoupons: GetCouponsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<CouponsUiState>(CouponsUiState.Loading)
        val state: StateFlow<CouponsUiState> = _state.asStateFlow()

        private var allCoupons: List<Coupon> = emptyList()
        private var selectedStore: String? = null

        init {
            load()
        }

        fun load() {
            _state.value = CouponsUiState.Loading
            viewModelScope.launch {
                getCoupons().fold(
                    onSuccess = {
                        allCoupons = it
                        selectedStore = null
                        publish()
                    },
                    onFailure = { _state.value = CouponsUiState.Error },
                )
            }
        }

        fun onStoreSelected(store: String?) {
            if (_state.value !is CouponsUiState.Success) return
            selectedStore = store
            publish()
        }

        private fun publish() {
            val now = System.currentTimeMillis()
            _state.value =
                CouponsUiState.Success(
                    coupons =
                        allCoupons
                            .filter { selectedStore == null || it.storeName == selectedStore }
                            .sortedForDisplay(now),
                    stores = allCoupons.map { it.storeName }.distinct().sorted(),
                    selectedStore = selectedStore,
                )
        }
    }
