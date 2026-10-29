package com.promoapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promoapp.domain.model.Deal
import com.promoapp.domain.repository.DealRepository
import com.promoapp.domain.repository.ProfileRepository
import com.promoapp.domain.repository.SettingsRepository
import com.promoapp.notifications.DailyDealScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MyDealsState {
    data object Loading : MyDealsState

    data object Error : MyDealsState

    data class Success(
        val deals: List<Deal>,
    ) : MyDealsState
}

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        profile: ProfileRepository,
        private val repository: DealRepository,
        private val settings: SettingsRepository,
        private val dailyDealScheduler: DailyDealScheduler,
    ) : ViewModel() {
        val displayName: StateFlow<String> =
            profile.displayName
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

        val dailyDealsEnabled: StateFlow<Boolean> =
            settings.dailyDealsEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        private val _myDeals = MutableStateFlow<MyDealsState>(MyDealsState.Loading)
        val myDeals: StateFlow<MyDealsState> = _myDeals.asStateFlow()

        /** Recarrega a lista. Se já havia dados, eles continuam na tela caso a rede falhe. */
        fun refresh() {
            viewModelScope.launch {
                repository.getMyDeals().fold(
                    onSuccess = { _myDeals.value = MyDealsState.Success(it) },
                    onFailure = { if (_myDeals.value !is MyDealsState.Success) _myDeals.value = MyDealsState.Error },
                )
            }
        }

        fun onDailyDealsChange(enabled: Boolean) {
            viewModelScope.launch {
                settings.setDailyDealsEnabled(enabled)
                dailyDealScheduler.setEnabled(enabled)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
