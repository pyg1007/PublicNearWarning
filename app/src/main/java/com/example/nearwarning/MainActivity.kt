package com.example.nearwarning

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SyncRequest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val permissionOK = 100
    private var permissionCheck = false
    private var isLocationUpdateStart = false

    private var location : Location? = null
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest()
        locationCallback = LocationCallback()
        startLocationUpdates()

        if (Build.VERSION.SDK_INT >= 23) {
            checkPermission()
        }

        val supportMapFragment: SupportMapFragment =
            supportFragmentManager.findFragmentById(R.id.GoogleMap) as SupportMapFragment
        supportMapFragment.getMapAsync(this)
    }

    private fun checkPermission() {
        val hasCoarseLocation: Int = ContextCompat.checkSelfPermission(this, permissions[0])
        val hasFineLocation: Int = ContextCompat.checkSelfPermission(this, permissions[1])

        if (hasCoarseLocation == PackageManager.PERMISSION_GRANTED && hasFineLocation == PackageManager.PERMISSION_GRANTED)
            permissionCheck = true
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                Toast.makeText(this@MainActivity, "지도를 사용하기 위해서는 위치권한이 필요합니다.", Toast.LENGTH_LONG)
                    .show()
                ActivityCompat.requestPermissions(this, permissions, permissionOK)
            } else {
                ActivityCompat.requestPermissions(this, permissions, permissionOK)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (permissions.size == grantResults.size && requestCode == permissionOK) {
            var check = true

            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check = false
                    break
                }
            }

            if (check) {
                permissionCheck = true
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        permissions[0]
                    ) or ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[1])
                )
                    Toast.makeText(
                        this@MainActivity,
                        "퍼미션이 거부되었습니다. 앱을 다시 실행시켜 퍼미션 허용을 해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                else
                    Toast.makeText(
                        this@MainActivity,
                        "퍼미션이 거부되었습니다. 설정에서 퍼미션 허용을 해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }
    }

    override fun onMapReady(p0: GoogleMap?) {
        googleMap = p0

        Log.e("permissions" , permissionCheck.toString())
        if (permissionCheck)
            getLocationUpdates(googleMap)
    }

    private fun getLocationUpdates(googleMap: GoogleMap?) {
        Log.e("A", "A")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest()
        locationRequest.interval = 5000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                Log.e("check : " , p0.toString())
                p0 ?: return

                if (p0.locations.isNotEmpty()) {
                    location = p0.lastLocation
                    Log.e("location : ", location!!.latitude.toString() +  location!!.longitude)
                    val currentLocation  = LatLng(location!!.latitude, location!!.longitude)
                    val markerOptions = MarkerOptions()
                    markerOptions.position(currentLocation).title("서울").snippet("수도")

                    googleMap!!.addMarker(markerOptions)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation))
                    googleMap.animateCamera(CameraUpdateFactory.zoomTo(20f))
                }
            }
        }
    }

    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
        Log.e("B","B")
        isLocationUpdateStart = true
    }

    private fun stopLocationUpdate() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdateStart = false
    }

    override fun onPause() {
        stopLocationUpdate()
        Log.e("PauseStartcheck : ", isLocationUpdateStart.toString())
        super.onPause()
    }

    override fun onResume() {

        Log.e("Startcheck : ", isLocationUpdateStart.toString())
        if (!isLocationUpdateStart)
            startLocationUpdates()
        Log.e("Startcheck : ", isLocationUpdateStart.toString())
        isLocationUpdateStart = true
        super.onResume()
    }
}
