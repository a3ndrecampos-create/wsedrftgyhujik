package com.promoapp.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promoapp.domain.model.PriceAlert
import com.promoapp.domain.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel
    @Inject
    constructor(
        private val repository: AlertRepository,
    ) : ViewModel() {
        /** `null` enquanto o banco ainda não respondeu. */
        val alerts: StateFlow<List<PriceAlert>?> =
            repository
                .observeAlerts()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private val _isChecking = MutableStateFlow(false)
        val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

        fun onDelete(dealId: Long) {
            viewModelScope.launch { repository.deleteAlert(dealId) }
        }

        fun onCheckNow() {
            if (_isChecking.value) return
            viewModelScope.launch {
                _isChecking.value = true
                try {
                    repository.checkAlerts()
                } finally {
                    _isChecking.value = false
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
