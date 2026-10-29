package com.promoapp.ui

import com.promoapp.domain.model.Coupon
import com.promoapp.domain.repository.CouponRepository
import com.promoapp.domain.usecase.GetCouponsUseCase
import com.promoapp.ui.coupons.CouponsUiState
import com.promoapp.ui.coupons.CouponsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CouponsViewModelTest {
    private class FakeCouponRepository(
        var result: Result<List<Coupon>>,
    ) : CouponRepository {
        override suspend fun getCoupons(): Result<List<Coupon>> = result
    }

    private val hour = 3_600_000L

    private fun coupon(
        id: Long,
        store: String,
        expiresInHours: Long?,
    ) = Coupon(
        id = id,
        code = "CODE$id",
        storeName = store,
        description = "Descrição",
        discountLabel = "10% OFF",
        minPurchase = null,
        expiresAtMillis = expiresInHours?.let { System.currentTimeMillis() + it * hour },
        url = "https://example.com",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads coupons sorted with expired last and lists stores`() {
        val repository =
            FakeCouponRepository(
                Result.success(
                    listOf(
                        coupon(1, "Magalu", expiresInHours = -2),
                        coupon(2, "Amazon", expiresInHours = 100),
                        coupon(3, "Amazon", expiresInHours = 3),
                    ),
                ),
            )

        val viewModel = CouponsViewModel(GetCouponsUseCase(repository))

        val state = viewModel.state.value as CouponsUiState.Success
        assertEquals(listOf(3L, 2L, 1L), state.coupons.map { it.id })
        assertEquals(listOf("Amazon", "Magalu"), state.stores)
    }

    @Test
    fun `selecting a store filters the list and null clears it`() {
        val repository =
            FakeCouponRepository(
                Result.success(
                    listOf(
                        coupon(1, "Magalu", expiresInHours = 100),
                        coupon(2, "Amazon", expiresInHours = 100),
                    ),
                ),
            )
        val viewModel = CouponsViewModel(GetCouponsUseCase(repository))

        viewModel.onStoreSelected("Amazon")
        val filtered = viewModel.state.value as CouponsUiState.Success
        assertEquals(listOf(2L), filtered.coupons.map { it.id })
        assertEquals("Amazon", filtered.selectedStore)

        viewModel.onStoreSelected(null)
        assertEquals(2, (viewModel.state.value as CouponsUiState.Success).coupons.size)
    }

    @Test
    fun `failure shows error and retry recovers`() {
        val repository = FakeCouponRepository(Result.failure(IOException("sem rede")))
        val viewModel = CouponsViewModel(GetCouponsUseCase(repository))
        assertTrue(viewModel.state.value is CouponsUiState.Error)

        repository.result = Result.success(listOf(coupon(1, "Amazon", expiresInHours = 10)))
        viewModel.load()

        assertTrue(viewModel.state.value is CouponsUiState.Success)
    }
}
