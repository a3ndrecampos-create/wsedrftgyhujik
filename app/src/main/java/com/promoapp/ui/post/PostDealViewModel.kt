package com.promoapp.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.DraftError
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.repository.ProfileRepository
import com.promoapp.domain.usecase.GetFilterOptionsUseCase
import com.promoapp.domain.usecase.PostDealUseCase
import com.promoapp.ui.search.parsePrice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostDealUiState(
    val title: String = "",
    val url: String = "",
    val store: String = "",
    val priceText: String = "",
    val originalPriceText: String = "",
    val imageUrl: String = "",
    val coupon: String = "",
    val category: String? = null,
    val freeShipping: Boolean = false,
    val author: String = "",
    val showErrors: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val options: FilterOptions? = null,
) {
    fun toDraft() =
        DealDraft(
            title = title,
            url = url,
            store = store,
            price = parsePrice(priceText),
            originalPrice = parsePrice(originalPriceText),
            imageUrl = imageUrl,
            couponCode = coupon,
            category = category,
            freeShipping = freeShipping,
            authorName = author,
        )

    /** Erros que devem aparecer na tela: só depois da primeira tentativa de publicar. */
    val visibleErrors: Set<DraftError>
        get() = if (showErrors) toDraft().validate() else emptySet()
}

@HiltViewModel
class PostDealViewModel
    @Inject
    constructor(
        private val postDeal: PostDealUseCase,
        getFilterOptions: GetFilterOptionsUseCase,
        private val profile: ProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PostDealUiState())
        val state: StateFlow<PostDealUiState> = _state.asStateFlow()

        private val postedEvents = Channel<Long>(Channel.BUFFERED)

        /** Emite o id da oferta publicada, uma única vez, para a tela navegar. */
        val postedDealId: Flow<Long> = postedEvents.receiveAsFlow()

        init {
            viewModelScope.launch {
                val name = profile.displayName.first()
                _state.update { if (it.author.isBlank()) it.copy(author = name) else it }
            }
            viewModelScope.launch {
                val options = getFilterOptions().getOrNull()
                _state.update { it.copy(options = options) }
            }
        }

        fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

        fun onUrlChange(value: String) = _state.update { it.copy(url = value) }

        fun onStoreChange(value: String) = _state.update { it.copy(store = value) }

        fun onPriceChange(value: String) = _state.update { it.copy(priceText = value) }

        fun onOriginalPriceChange(value: String) = _state.update { it.copy(originalPriceText = value) }

        fun onImageUrlChange(value: String) = _state.update { it.copy(imageUrl = value) }

        fun onCouponChange(value: String) = _state.update { it.copy(coupon = value) }

        fun onAuthorChange(value: String) = _state.update { it.copy(author = value) }

        fun onFreeShippingChange(value: Boolean) = _state.update { it.copy(freeShipping = value) }

        fun onCategoryToggle(name: String) = _state.update { it.copy(category = if (it.category == name) null else name) }

        fun submit() {
            val current = _state.value
            if (current.isSubmitting) return
            val draft = current.toDraft()
            if (draft.validate().isNotEmpty()) {
                _state.update { it.copy(showErrors = true) }
                return
            }
            _state.update { it.copy(isSubmitting = true, submitFailed = false, showErrors = true) }
            viewModelScope.launch {
                postDeal(draft).fold(
                    onSuccess = { deal ->
                        profile.setDisplayName(draft.authorName)
                        _state.update { it.copy(isSubmitting = false) }
                        postedEvents.send(deal.id)
                    },
                    onFailure = { _state.update { it.copy(isSubmitting = false, submitFailed = true) } },
                )
            }
        }
    }
