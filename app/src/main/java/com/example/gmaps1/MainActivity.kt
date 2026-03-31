package com.example.gmaps1

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gmaps1.ui.theme.Gmaps1Theme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Gmaps1Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationMapScreen()
                }
            }
        }
    }
}

@Composable
fun LocationMapScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val scope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Allston, MA fallback location
    val allstonLocation = LatLng(42.3539, -71.1329)

    var currentLocation by remember { mutableStateOf(allstonLocation) }
    var addressText by remember { mutableStateOf("Loading address...") }
    var isUsingLiveLocation by remember { mutableStateOf(false) }

    val customMarkers = remember { mutableStateListOf<LatLng>() }

    val cameraPositionState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(allstonLocation, 14f)
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val liveLatLng = LatLng(location.latitude, location.longitude)
                    currentLocation = liveLatLng
                    isUsingLiveLocation = true
                    addressText = getAddress(context, liveLatLng)
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(liveLatLng, 16f),
                            1000
                        )
                    }
                } else {
                    currentLocation = allstonLocation
                    isUsingLiveLocation = false
                    addressText = getAddress(context, allstonLocation)
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(allstonLocation, 14f),
                            1000
                        )
                    }
                }
            }
        } else {
            currentLocation = allstonLocation
            isUsingLiveLocation = false
            addressText = getAddress(context, allstonLocation)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = true,
                compassEnabled = true
            ),
            onMapClick = { latLng ->
                customMarkers.add(latLng)
            }
        ) {
            Marker(
                state = MarkerState(position = currentLocation),
                title = if (isUsingLiveLocation) "Your Location" else "Allston, MA",
                snippet = addressText
            )

            customMarkers.forEachIndexed { index, markerPos ->
                Marker(
                    state = MarkerState(position = markerPos),
                    title = "Custom Marker ${index + 1}",
                    snippet = "Lat: ${markerPos.latitude}, Lng: ${markerPos.longitude}"
                )
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (isUsingLiveLocation) "Current Address" else "Fallback Address (Allston, MA)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = addressText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun getAddress(context: Context, latLng: LatLng): String {
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        @Suppress("DEPRECATION")
        val addresses: List<Address>? =
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            val lines = mutableListOf<String>()
            for (i in 0..address.maxAddressLineIndex) {
                lines.add(address.getAddressLine(i))
            }
            lines.joinToString(", ")
        } else {
            "No address found"
        }
    } catch (e: Exception) {
        "Error fetching address"
    }
}