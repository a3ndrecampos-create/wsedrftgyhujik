package com.promoapp

import androidx.paging.PagingData
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.DealDetail
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.DealTab
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.repository.DealRepository
import com.promoapp.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Repositório falso para testes de ViewModel. Só o que os testes usam tem comportamento. */
class FakeDealRepository : DealRepository {
    var postResult: Result<Deal> = Result.success(testDeal())
    var filterOptions: Result<FilterOptions> = Result.success(FilterOptions(listOf("Amazon"), listOf("Áudio")))
    val postedDrafts = mutableListOf<DealDraft>()

    override fun getDeals(tab: DealTab): Flow<PagingData<Deal>> = emptyFlow()

    override fun searchDeals(filters: SearchFilters): Flow<PagingData<Deal>> = emptyFlow()

    override suspend fun getFilterOptions(): Result<FilterOptions> = filterOptions

    override suspend fun getDealDetail(id: Long): Result<DealDetail> = error("não usado nestes testes")

    override suspend fun postDeal(draft: DealDraft): Result<Deal> {
        postedDrafts += draft
        return postResult
    }

    override suspend fun getMyDeals(): Result<List<Deal>> = Result.success(emptyList())

    override suspend fun getDailyCandidates(): Result<List<Deal>> = Result.success(emptyList())

    override fun observeFavoriteIds(): Flow<List<Long>> = flowOf(emptyList())

    override suspend fun setFavorite(
        dealId: Long,
        favorite: Boolean,
    ) = Unit
}

class FakeProfileRepository(
    initialName: String = "",
) : ProfileRepository {
    private val name = MutableStateFlow(initialName)
    override val displayName: Flow<String> = name

    override suspend fun setDisplayName(name: String) {
        this.name.value = name
    }
}
