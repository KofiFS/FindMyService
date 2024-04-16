package com.example.findmyservice.ui.home

import CellTowerData
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
//import com.example.findmyservice.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
//import com.example.findmyservice.databinding.FragmentHomeBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import androidx.lifecycle.ViewModelProvider
import com.example.findmyservice.R
import com.example.findmyservice.databinding.FragmentHomeBinding
import com.google.android.gms.maps.model.CircleOptions
import java.io.BufferedReader
import java.io.InputStreamReader


class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var telephonyManager: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var signalStrengthUpdateRunnable: Runnable
    private lateinit var autocompleteFragment:AutocompleteSupportFragment

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        Places.initialize(requireContext(), getString(R.string.google_map_api_key))

        autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as
                AutocompleteSupportFragment

        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG))

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val add = place.address
                val id = place.id
                val latLng = place.latLng!!

                // Show a Toast message when a place is selected
                Toast.makeText(context, "Selected place: ${place.address}", Toast.LENGTH_LONG).show()
                zoomOnMap(latLng)
            }

            override fun onError(status: Status) {
                // Show a Toast message when there is an error selecting a place
                Toast.makeText(context, "Error: ${status.statusMessage}", Toast.LENGTH_LONG).show()
            }
        })

        telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        setupSignalStrengthListener()

        val root: View = binding.root

        homeViewModel.text.observe(viewLifecycleOwner) {
        }
        return root
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        checkPermissions()
        enableMyLocation()
        val data = readCsvData(requireContext())
        googleMap?.let { map ->
            displayCoverageOnMap(map, data)
        }
    }

    private fun zoomOnMap(latlng:LatLng)
    {
        val newLatLngZoom = CameraUpdateFactory.newLatLngZoom(latlng, 12f) // 12f -> amount of zoom level
        googleMap?.animateCamera(newLatLngZoom)
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentUserLocation = LatLng(it.latitude, it.longitude)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation, 12f))
                }
            }
        }
    }

    private fun setupSignalStrengthListener()  {
        signalStrengthUpdateRunnable = Runnable {
            // Register the listener to the telephony manager
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    super.onSignalStrengthsChanged(signalStrength)
                    val signalInfo = signalStrength.level  // This requires API level 23 or higher

                    // Update the TextView directly from the Fragment's root view
                    val signalStrengthTextView = view?.findViewById<TextView>(R.id.signalStrengthText)
                    activity?.runOnUiThread {
                        signalStrengthTextView?.text = "My Signal Strength: $signalInfo"
                    }
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

            // Reschedule this runnable to run again after 3000 milliseconds
            handler.postDelayed(this.signalStrengthUpdateRunnable, 10000)
        }

        // Initially start the repeating task
        handler.post(signalStrengthUpdateRunnable)
    }

    private fun setupAutocompleteFragment(fragment: AutocompleteSupportFragment) {
        fragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG))
        fragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val latLng = place.latLng
                if (latLng != null) {
                    Toast.makeText(context, "Selected place: ${place.address}", Toast.LENGTH_LONG)
                        .show()
                    zoomOnMap(latLng)
                } else {
                    Toast.makeText(context, "No location for selected place.", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onError(status: Status) {
                Toast.makeText(
                    context,
                    "Error selecting place: ${status.statusMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    fun displayCoverageOnMap(googleMap: GoogleMap, data: List<CellTowerData>) {
        data.forEach { item ->
            // Determine color based on averageSignal, with a fallback for zero values
            val color = when {
                item.averageSignal > 0 -> { // Valid averageSignal values
                    when {
                        item.averageSignal >= -75 -> 0x6600FF00 // Strong signal (green, semi-transparent)
                        item.averageSignal >= -85 -> 0x66FFFF00 // Moderate signal (yellow, semi-transparent)
                        else -> 0x66FF0000 // Weak signal (red, semi-transparent)
                    }
                }
                item.samples > 10 -> 0x66FFFFFF // Use white for moderate confidence if there are some samples
                else -> 0x660000FF // Default color (blue, semi-transparent) if averageSignal is 0 or unavailable
            }

            // Adjust the radius to be more indicative of coverage confidence
            val radius = if (item.samples > 10) item.range.toDouble() else item.range.toDouble() / 2

            // Create and add the circle to the map
            val circleOptions = CircleOptions()
                .center(LatLng(item.latitude, item.longitude))
                .radius(radius) // Use adjusted radius
                .fillColor(color)
                .strokeColor(Color.BLACK)
            googleMap.addCircle(circleOptions)

            // Add a marker for each cell tower to handle clicks
            val markerOptions = MarkerOptions()
                .position(LatLng(item.latitude, item.longitude))
                .title("Signal Strength: ${item.averageSignal}")
            val marker = googleMap.addMarker(markerOptions)
            marker?.tag = item  // Storing the entire item as tag for later use in click listener
        }

        // Set a marker click listener
        googleMap.setOnMarkerClickListener { marker ->
            val cellTowerData = marker.tag as? CellTowerData
            cellTowerData?.let {
                val datasignalStrengthTextView = view?.findViewById<TextView>(R.id.datassignalStrengthText)
                // Update the TextView with the signal strength
                datasignalStrengthTextView?.text = "Locations Strength: ${it.averageSignal}"
            }
            true // Return true to indicate that we have handled the event

        }
    }

    fun readCsvData(context: Context): List<CellTowerData> {
        val inputStream = context.resources.openRawResource(R.raw.cell_tower_xxs_usa_2024)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val dataList = mutableListOf<CellTowerData>()
        reader.readLine() // Skip the header line if your CSV has one
        reader.forEachLine { line ->
            val tokens = line.split(",")
            if (tokens.size == 14) {
                val data = CellTowerData(
                    radio = tokens[0],
                    mcc = tokens[1].toInt(),
                    net = tokens[2].toInt(),
                    area = tokens[3].toInt(),
                    cell = tokens[4].toLong(),
                    unit = tokens[5].toInt(),
                    longitude = tokens[6].toDouble(),
                    latitude = tokens[7].toDouble(),
                    range = tokens[8].toInt(),
                    samples = tokens[9].toInt(),
                    changeable = tokens[10].toInt(),
                    created = tokens[11].toLong(),
                    updated = tokens[12].toLong(),
                    averageSignal = tokens[13].toInt()
                )
                dataList.add(data)
            }
        }
        return dataList
    }


    override fun onResume() {
        super.onResume()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding = null
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}