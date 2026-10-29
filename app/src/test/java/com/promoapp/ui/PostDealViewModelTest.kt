package com.promoapp.ui

import com.promoapp.FakeDealRepository
import com.promoapp.FakeProfileRepository
import com.promoapp.domain.model.DraftError
import com.promoapp.domain.usecase.GetFilterOptionsUseCase
import com.promoapp.domain.usecase.PostDealUseCase
import com.promoapp.testDeal
import com.promoapp.ui.post.PostDealViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PostDealViewModelTest {

    private val repository = FakeDealRepository()
    private val profile = FakeProfileRepository(initialName = "Ana")

    private fun viewModel() = PostDealViewModel(
        postDeal = PostDealUseCase(repository),
        getFilterOptions = GetFilterOptionsUseCase(repository),
        profile = profile,
    )

    private fun PostDealViewModel.fillValidForm() {
        onTitleChange("Fone Bluetooth com cancelamento de ruído")
        onUrlChange("https://loja.com/produto")
        onStoreChange("Amazon")
        onPriceChange("199,90")
        onOriginalPriceChange("399")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `author is prefilled from the profile and options are loaded`() {
        val vm = viewModel()

        assertEquals("Ana", vm.state.value.author)
        assertEquals(listOf("Amazon"), vm.state.value.options?.stores)
    }

    @Test
    fun `errors stay hidden until the first submit`() {
        val vm = viewModel()
        vm.onTitleChange("Oi")

        assertTrue(vm.state.value.visibleErrors.isEmpty())

        vm.submit()

        assertTrue(vm.state.value.showErrors)
        assertTrue(DraftError.TITLE_TOO_SHORT in vm.state.value.visibleErrors)
        assertTrue(repository.postedDrafts.isEmpty())
    }

    @Test
    fun `valid form is sent, name is saved and the new id is emitted`() = runTest {
        repository.postResult = Result.success(testDeal().copy(id = 10_001))
        val vm = viewModel()
        vm.fillValidForm()
        vm.onAuthorChange("Ana Paula")

        vm.submit()

        assertEquals(10_001L, vm.postedDealId.first())
        assertEquals("Ana Paula", profile.displayName.first())
        assertEquals(199.9, repository.postedDrafts.single().price!!, 0.001)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `network failure keeps the form and shows the error`() {
        repository.postResult = Result.failure(IOException("sem rede"))
        val vm = viewModel()
        vm.fillValidForm()

        vm.submit()

        assertTrue(vm.state.value.submitFailed)
        assertFalse(vm.state.value.isSubmitting)
        assertEquals("Amazon", vm.state.value.store)
    }

    @Test
    fun `category chip toggles on and off`() {
        val vm = viewModel()

        vm.onCategoryToggle("Áudio")
        assertEquals("Áudio", vm.state.value.category)

        vm.onCategoryToggle("Áudio")
        assertNull(vm.state.value.category)
    }
}
