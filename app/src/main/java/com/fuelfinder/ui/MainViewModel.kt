package com.fuelfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelfinder.data.FuelRepository
import com.fuelfinder.data.model.FuelStation
import com.fuelfinder.data.model.RouteInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LocationSuggestion(
    val displayName: String,
    val shortName: String,
    val lat: Double,
    val lon: Double
)

sealed class UiState {
    object Idle : UiState()
    data class Loading(val message: String = "Searching...") : UiState()
    data class Success(val stations: List<FuelStation>, val route: RouteInfo) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {

    private val repository = FuelRepository()

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    // Autocomplete suggestions for From field
    private val _fromSuggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    val fromSuggestions: StateFlow<List<LocationSuggestion>> = _fromSuggestions

    // Autocomplete suggestions for To field
    private val _toSuggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    val toSuggestions: StateFlow<List<LocationSuggestion>> = _toSuggestions

    private var fromJob: Job? = null
    private var toJob: Job? = null

    // ── Autocomplete ──────────────────────────────────────────────────────────

//    fun onFromTyping(query: String) {
//        fromJob?.cancel()
//        if (query.length < 3) { _fromSuggestions.value = emptyList(); return }
//        fromJob = viewModelScope.launch {
//            delay(350) // debounce 350ms
//            _fromSuggestions.value = repository.getSuggestions(query)
//        }
//    }

//    fun onToTyping(query: String) {
//        toJob?.cancel()
//        if (query.length < 3) { _toSuggestions.value = emptyList(); return }
//        toJob = viewModelScope.launch {
//            delay(350)
//            _toSuggestions.value = repository.getSuggestions(query)
//        }
//    }

    fun clearFromSuggestions() { _fromSuggestions.value = emptyList() }
    fun clearToSuggestions()   { _toSuggestions.value = emptyList() }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(from: String, to: String) {
        if (from.isBlank() || to.isBlank()) {
            _state.value = UiState.Error("Please enter both start and destination")
            return
        }

        viewModelScope.launch {
            try {
                _state.value = UiState.Loading("Finding $from...")
                val fromCoords = repository.geocode(from)
                if (fromCoords == null) {
                    _state.value = UiState.Error("Could not find: $from\nTry a more specific name")
                    return@launch
                }

                _state.value = UiState.Loading("Finding $to...")
                val toCoords = repository.geocode(to)
                if (toCoords == null) {
                    _state.value = UiState.Error("Could not find: $to\nTry a more specific name")
                    return@launch
                }

                _state.value = UiState.Loading("Loading petrol pumps from OpenStreetMap...")
                val stations = repository.fetchRealPetrolPumps(
                    fromCoords.first, fromCoords.second,
                    toCoords.first, toCoords.second
                )

                val route = repository.buildRoute(
                    from, to,
                    fromCoords.first, fromCoords.second,
                    toCoords.first, toCoords.second
                )

                if (stations.isEmpty()) {
                    _state.value = UiState.Error(
                        "No petrol pumps found along this route.\nTry a longer route or different locations."
                    )
                } else {
                    _state.value = UiState.Success(stations, route)
                }

            } catch (e: Exception) {
                _state.value = UiState.Error("Network error: ${e.localizedMessage}\nCheck your internet connection.")
            }
        }
    }

    fun reset() {
        _state.value = UiState.Idle
        _fromSuggestions.value = emptyList()
        _toSuggestions.value = emptyList()
    }
}
