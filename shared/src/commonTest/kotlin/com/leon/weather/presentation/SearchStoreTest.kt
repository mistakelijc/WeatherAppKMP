package com.leon.weather.presentation

import com.leon.weather.MemorySearchHistory
import com.leon.weather.data.repository.AppError
import com.leon.weather.data.repository.AppResult
import com.leon.weather.data.repository.LocationRepository
import com.leon.weather.model.Location
import com.leon.weather.model.PopularLocations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchStoreTest {
    private val scopes = mutableListOf<kotlinx.coroutines.CoroutineScope>()
    @AfterTest fun cleanup() { scopes.forEach { it.cancel() } }
    private fun TestScope.storeScope() = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
    ).also { scopes += it }

    @Test fun `rapid input is debounced and trimmed before requesting`() = runTest {
        val queries = mutableListOf<String>()
        val vm = makeSearchStore(repository { queries += it; AppResult.Success(emptyList()) }, "", MemorySearchHistory())
        vm.onQueryChanged("Bei")
        advanceTimeBy(150)
        vm.onQueryChanged(" Beijing ")
        advanceTimeBy(299)
        assertTrue(queries.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("Beijing"), queries)
        assertEquals(SearchStatus.Empty, vm.uiState.value.status)
    }

    @Test fun `blank and one character queries do not hit API`() = runTest {
        var calls = 0
        val vm = makeSearchStore(repository { calls++; AppResult.Success(emptyList()) }, "", MemorySearchHistory())
        vm.onQueryChanged("北")
        advanceUntilIdle()
        assertEquals(SearchStatus.TooShort, vm.uiState.value.status)
        vm.onQueryChanged("  ")
        advanceUntilIdle()
        assertEquals(SearchStatus.Idle, vm.uiState.value.status)
        assertEquals(0, calls)
    }

    @Test fun `stale response cannot replace newer results even if cancellation is ignored`() = runTest {
        val old = CompletableDeferred<AppResult<List<Location>>>()
        val new = CompletableDeferred<AppResult<List<Location>>>()
        val vm = makeSearchStore(repository { query -> withContext(NonCancellable) {
            if (query == "北京") old.await() else new.await()
        } }, "", MemorySearchHistory())
        vm.onQueryChanged("北京")
        advanceTimeBy(300); runCurrent()
        vm.onQueryChanged("上海")
        advanceTimeBy(300); runCurrent()
        new.complete(AppResult.Success(listOf(PopularLocations.all[1])))
        runCurrent()
        old.complete(AppResult.Success(listOf(PopularLocations.all[0])))
        advanceUntilIdle()
        assertEquals("上海", vm.uiState.value.query)
        assertEquals("上海", (vm.uiState.value.status as SearchStatus.Results).locations.single().name)
    }

    @Test fun `clearing search prevents a late response from repopulating results`() = runTest {
        val pending = CompletableDeferred<AppResult<List<Location>>>()
        val savedState = ""
        val vm = makeSearchStore(repository { withContext(NonCancellable) { pending.await() } }, savedState, MemorySearchHistory())
        vm.onQueryChanged("北京")
        advanceTimeBy(300); runCurrent()
        vm.onQueryChanged("")
        pending.complete(AppResult.Success(PopularLocations.all))
        advanceUntilIdle()
        assertEquals(SearchStatus.Idle, vm.uiState.value.status)
        assertEquals("", vm.uiState.value.query)
    }

    @Test fun `retry works for the same keyword after failure`() = runTest {
        var calls = 0
        val vm = makeSearchStore(repository {
            if (++calls == 1) AppResult.Failure(AppError.Network) else AppResult.Success(PopularLocations.all)
        }, "", MemorySearchHistory())
        vm.onQueryChanged("北京")
        advanceUntilIdle()
        assertEquals(SearchStatus.Error(AppError.Network), vm.uiState.value.status)
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is SearchStatus.Results)
        assertEquals(2, calls)
    }

    @Test fun `restored query is searched again after process recreation`() = runTest {
        val state = "北京"
        val queries = mutableListOf<String>()
        val vm = makeSearchStore(repository { queries += it; AppResult.Success(PopularLocations.all) }, state, MemorySearchHistory())
        advanceUntilIdle()
        assertEquals(listOf("北京"), queries)
        assertEquals("北京", vm.uiState.value.query)
    }

    private fun TestScope.makeSearchStore(repository: LocationRepository, initialQuery: String, history: com.leon.weather.data.repository.SearchHistoryRepository) =
        SearchStore(repository, history, storeScope(), initialQuery)

    private fun repository(block: suspend (String) -> AppResult<List<Location>>) = object : LocationRepository {
        override suspend fun search(query: String) = block(query)
    }

    @Test fun `typing does not save history but selecting a city clears input and records it`() = runTest {
        val history = MemorySearchHistory()
        val saved = ""
        val vm = makeSearchStore(repository { AppResult.Success(PopularLocations.all) }, saved, history)
        vm.onQueryChanged("北京")
        advanceUntilIdle()
        assertTrue(history.locations.value.isEmpty())
        vm.onLocationSelected(PopularLocations.all.first())
        advanceUntilIdle()
        assertEquals(SearchStatus.Idle, vm.uiState.value.status)
        assertEquals(listOf(PopularLocations.all.first()), vm.uiState.value.history)
        vm.onQueryChanged("上海")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.history.size)
    }

    @Test fun `history write failure does not prevent clearing selected search`() = runTest {
        val history = object : com.leon.weather.data.repository.SearchHistoryRepository by MemorySearchHistory() {
            override suspend fun record(location: Location) { throw okio.IOException("disk unavailable") }
        }
        val vm = makeSearchStore(repository { AppResult.Success(PopularLocations.all) }, "", history)
        vm.onQueryChanged("北京")
        advanceUntilIdle()
        vm.onLocationSelected(PopularLocations.all.first())
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.query)
        assertTrue(vm.uiState.value.historyUpdateFailed)
    }

    @Test fun `removing and clearing history updates home without starting a search`() = runTest {
        val history = MemorySearchHistory()
        PopularLocations.all.forEach { history.record(it) }
        var searches = 0
        val vm = makeSearchStore(repository { searches++; AppResult.Success(emptyList()) }, "", history)
        advanceUntilIdle()
        vm.removeHistory(PopularLocations.all[1].id)
        advanceUntilIdle()
        assertEquals(PopularLocations.all.reversed().filterNot { it.id == PopularLocations.all[1].id }, vm.uiState.value.history)
        vm.clearHistory()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.history.isEmpty())
        assertEquals(SearchStatus.Idle, vm.uiState.value.status)
        assertEquals(0, searches)
    }

    @Test fun `failed deletion retains history and successful retry clears the error`() = runTest {
        val memory = MemorySearchHistory()
        val city = PopularLocations.all.first()
        memory.record(city)
        var fail = true
        val history = object : com.leon.weather.data.repository.SearchHistoryRepository by memory {
            override suspend fun remove(locationId: Long) {
                if (fail) throw okio.IOException("disk unavailable")
                memory.remove(locationId)
            }
            override suspend fun clear() {
                if (fail) throw okio.IOException("disk unavailable")
                memory.clear()
            }
        }
        val vm = makeSearchStore(repository { AppResult.Success(emptyList()) }, "", history)
        vm.removeHistory(city.id)
        advanceUntilIdle()
        assertEquals(listOf(city), vm.uiState.value.history)
        assertTrue(vm.uiState.value.historyUpdateFailed)
        vm.clearHistory()
        advanceUntilIdle()
        assertEquals(listOf(city), vm.uiState.value.history)
        assertTrue(vm.uiState.value.historyUpdateFailed)
        fail = false
        vm.clearHistory()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.history.isEmpty())
        assertFalse(vm.uiState.value.historyUpdateFailed)
    }
}
