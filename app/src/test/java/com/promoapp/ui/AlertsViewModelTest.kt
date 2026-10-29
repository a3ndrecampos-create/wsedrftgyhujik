package com.promoapp.ui

import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.PriceAlert
import com.promoapp.domain.repository.AlertRepository
import com.promoapp.ui.alerts.AlertsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {

    private class FakeAlertRepository(initial: List<PriceAlert>) : AlertRepository {
        val alerts = MutableStateFlow(initial)
        var checkCalls = 0

        override fun observeAlerts(): Flow<List<PriceAlert>> = alerts
        override fun observeAlert(dealId: Long): Flow<PriceAlert?> = alerts.map { list -> list.firstOrNull { it.dealId == dealId } }
        override suspend fun createAlert(deal: Deal, targetPrice: Double) = Unit
        override suspend fun deleteAlert(dealId: Long) {
            alerts.value = alerts.value.filterNot { it.dealId == dealId }
        }
        override suspend fun checkAlerts(): List<PriceAlert> {
            checkCalls++
            return emptyList()
        }
    }

    private fun alert(id: Long) = PriceAlert(id, "Produto $id", "", "Loja", "u", 10.0, 20.0, 20.0, id, null)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete removes the alert from the list`() = runTest {
        val repository = FakeAlertRepository(listOf(alert(1), alert(2)))
        val viewModel = AlertsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.alerts.collect {} }

        viewModel.onDelete(1)

        assertEquals(listOf(2L), viewModel.alerts.value?.map { it.dealId })
    }

    @Test
    fun `check now calls the repository and releases the button`() = runTest {
        val repository = FakeAlertRepository(listOf(alert(1)))
        val viewModel = AlertsViewModel(repository)

        viewModel.onCheckNow()

        assertEquals(1, repository.checkCalls)
        assertFalse(viewModel.isChecking.value)
    }
}
