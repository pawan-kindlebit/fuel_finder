package com.fuelfinder.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuelfinder.R
import com.fuelfinder.data.model.FuelStation
import com.fuelfinder.data.model.RouteInfo
import com.fuelfinder.databinding.ActivityMainBinding
import com.fuelfinder.databinding.BottomSheetPumpsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var currentStations: List<FuelStation> = emptyList()
    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var fromAdapter: SuggestionAdapter
    private lateinit var toAdapter: SuggestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMap()
        setupListeners()
        observe()
        setupSuggestionAdapters()
        setupAutocomplete()
        observeSuggestions()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
        }
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            hideKeyboard()
            val from = binding.etFrom.text?.toString().orEmpty().trim()
            val to = binding.etTo.text?.toString().orEmpty().trim()
            viewModel.search(from, to)
        }

        binding.etTo.setOnEditorActionListener { _, _, _ ->
            binding.btnSearch.performClick(); true
        }

        binding.btnClear.setOnClickListener {
            binding.etFrom.text?.clear()
            binding.etTo.text?.clear()
            currentStations = emptyList()
            bottomSheetDialog?.dismiss()
            viewModel.reset()
            setupMap()
        }

        // ⛽ FAB click → show bottom sheet with pump list
        binding.btnShowList.setOnClickListener {
            showPumpListBottomSheet()
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is UiState.Idle -> showIdle()
                    is UiState.Loading -> showLoading(state.message)
                    is UiState.Success -> showSuccess(state.stations, state.route)
                    is UiState.Error -> showError(state.message)
                }
            }
        }
    }
    private fun setupSuggestionAdapters() {
        fromAdapter = SuggestionAdapter { s ->
            binding.etFrom.setText(s.shortName)
            binding.etFrom.setSelection(s.shortName.length)
            viewModel.clearFromSuggestions()
            binding.rvFromSuggestions.visibility = View.GONE
            binding.etTo.requestFocus()
        }
        binding.rvFromSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvFromSuggestions.adapter = fromAdapter

        toAdapter = SuggestionAdapter { s ->
            binding.etTo.setText(s.shortName)
            binding.etTo.setSelection(s.shortName.length)
            viewModel.clearToSuggestions()
            binding.rvToSuggestions.visibility = View.GONE
            hideKeyboard()
        }
        binding.rvToSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvToSuggestions.adapter = toAdapter
    }

    private fun setupAutocomplete() {
        binding.etFrom.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
//                viewModel.onFromTyping(s?.toString().orEmpty())
            }
        })
        binding.etTo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
//                viewModel.onToTyping(s?.toString().orEmpty())
            }
        })
    }

    private fun observeSuggestions() {
        lifecycleScope.launch {
            viewModel.fromSuggestions.collect { suggestions ->
                if (suggestions.isEmpty()) {
                    binding.rvFromSuggestions.visibility = View.GONE
                } else {
                    fromAdapter.submitList(suggestions)
                    binding.rvFromSuggestions.visibility = View.VISIBLE
                }
            }
        }
        lifecycleScope.launch {
            viewModel.toSuggestions.collect { suggestions ->
                if (suggestions.isEmpty()) {
                    binding.rvToSuggestions.visibility = View.GONE
                } else {
                    toAdapter.submitList(suggestions)
                    binding.rvToSuggestions.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.btnSearch.isEnabled = true
        binding.btnSearch.text = "Find Fuel Stations"
        binding.btnClear.visibility = View.GONE
        binding.tvLoadingStatus.visibility = View.GONE
        binding.btnShowList.visibility = View.GONE
        binding.layoutRouteChip.visibility = View.GONE
    }

    private fun showLoading(message: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.btnSearch.isEnabled = false
        binding.btnSearch.text = "Searching..."
        binding.tvLoadingStatus.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = "  🔍  $message"
        binding.btnShowList.visibility = View.GONE
    }

    private fun showSuccess(stations: List<FuelStation>, route: RouteInfo) {
        currentStations = stations
        binding.progressBar.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.btnSearch.isEnabled = true
        binding.btnSearch.text = "Find Fuel Stations"
        binding.btnClear.visibility = View.VISIBLE
        binding.tvLoadingStatus.visibility = View.GONE

        // Show route chip on map
        binding.layoutRouteChip.visibility = View.VISIBLE
        binding.tvRouteFrom.text = route.fromName
        binding.tvRouteTo.text = route.toName
        binding.tvRouteDistance.text = "• ${String.format("%.0f", route.totalDistanceKm)} km"

        // Show fuel FAB with count
        binding.btnShowList.visibility = View.VISIBLE
        binding.tvPumpCount.text = "${stations.size} pumps"

        drawMap(stations, route)
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvError.text = message
        binding.layoutEmpty.visibility = View.GONE
        binding.btnSearch.isEnabled = true
        binding.btnSearch.text = "Find Fuel Stations"
        binding.tvLoadingStatus.visibility = View.GONE
        binding.btnShowList.visibility = View.GONE
    }

    // ── Bottom Sheet with pump list ───────────────────────────────────────────
    private fun showPumpListBottomSheet() {
        if (currentStations.isEmpty()) return

        bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
        val sheetBinding = BottomSheetPumpsBinding.inflate(LayoutInflater.from(this))

        sheetBinding.tvSheetTitle.text = "⛽  Petrol Pumps"
        sheetBinding.tvSheetSubtitle.text = "${currentStations.size} pumps • Sorted ₹ Low → High"

        val adapter = FuelStationAdapter { station ->
            // Click station → fly to it on map
            bottomSheetDialog?.dismiss()
            binding.mapView.controller.animateTo(GeoPoint(station.lat, station.lng))
            binding.mapView.controller.setZoom(15.0)
        }
        sheetBinding.rvSheetStations.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvSheetStations.adapter = adapter
        adapter.submitList(currentStations)

        sheetBinding.btnCloseSheet.setOnClickListener {
            bottomSheetDialog?.dismiss()
        }

        bottomSheetDialog?.setContentView(sheetBinding.root)
        bottomSheetDialog?.show()
    }

    // ── Draw map with route + markers ─────────────────────────────────────────
    private fun drawMap(stations: List<FuelStation>, route: RouteInfo) {
        binding.mapView.overlays.clear()

        val fromPt = GeoPoint(route.fromLat, route.fromLng)
        val toPt   = GeoPoint(route.toLat,   route.toLng)

        // Start marker
        Marker(binding.mapView).apply {
            position = fromPt
            title = "🟢 Start: ${route.fromName}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }.also { binding.mapView.overlays.add(it) }

        // End marker
        Marker(binding.mapView).apply {
            position = toPt
            title = "🔴 Destination: ${route.toName}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }.also { binding.mapView.overlays.add(it) }

        // Fuel station markers
        stations.forEach { s ->
            Marker(binding.mapView).apply {
                position = GeoPoint(s.lat, s.lng)
                title = s.name
                snippet = if (s.isBestOption)
                    "⭐ BEST — ₹${String.format("%.2f", s.price)}/L"
                else
                    "₹${String.format("%.2f", s.price)}/L • ${s.distanceFromRoute} km off route"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }.also { binding.mapView.overlays.add(it) }
        }

        // Zoom to fit
        val allLats = listOf(route.fromLat, route.toLat) + stations.map { it.lat }
        val allLngs = listOf(route.fromLng, route.toLng) + stations.map { it.lng }
        binding.mapView.zoomToBoundingBox(
            BoundingBox(allLats.max() + 0.3, allLngs.max() + 0.3,
                allLats.min() - 0.3, allLngs.min() - 0.3),
            true, 80
        )

        // Fetch real road route in background
        lifecycleScope.launch {
            val roadPoints = fetchRoadRoute(route.fromLat, route.fromLng, route.toLat, route.toLng)
            val points = if (roadPoints.isNotEmpty()) roadPoints
            else listOf(fromPt, toPt) // fallback to straight line

            val line = Polyline().apply {
                points.forEach { addPoint(it) }
                outlinePaint.color = Color.parseColor("#1A237E")
                outlinePaint.strokeWidth = 10f
                outlinePaint.alpha = 210
            }
            binding.mapView.overlays.add(0, line) // add behind markers
            binding.mapView.invalidate()
        }
    }
    private suspend fun fetchRoadRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): List<GeoPoint> {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "$fromLon,$fromLat;$toLon,$toLat" +
                    "?overview=full&geometries=geojson"

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "FuelFinderApp/1.0")
                .build()

            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val body = response.body?.string() ?: return emptyList()

            val json      = org.json.JSONObject(body)
            val routes    = json.getJSONArray("routes")
            if (routes.length() == 0) return emptyList()

            val geometry  = routes.getJSONObject(0).getJSONObject("geometry")
            val coords    = geometry.getJSONArray("coordinates")

            val points = mutableListOf<GeoPoint>()
            for (i in 0 until coords.length()) {
                val pt  = coords.getJSONArray(i)
                val lon = pt.getDouble(0) // GeoJSON is [lon, lat]
                val lat = pt.getDouble(1)
                points.add(GeoPoint(lat, lon))
            }
            points
        } catch (e: Exception) {
            android.util.Log.e("Route", "Road route error: ${e.message}")
            emptyList()
        }
    }
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
}