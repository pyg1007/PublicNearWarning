package com.example.nearwarning

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val permissionOK = 100
    private var warning: Warning? = null
    private var first: Boolean = false
    private var dialog: Dialog? = null

    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager

    private var targetLocation: Location? = null
    private var location: Location? = null
    private var currentMarker: Marker? = null
    private var currentLocation: Location? = null
    private var currentLatLng: LatLng? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationRequest =
            LocationRequest().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(3000)
                .setFastestInterval(1500)

        val locationSettingsRequest = LocationSettingsRequest.Builder()
        locationSettingsRequest.addLocationRequest(locationRequest)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            val alertDialog = AlertDialog.Builder(this).apply {
                this.setMessage("해당기능을 사용하기 위해서는 GPS기능이 필요합니다. 해당기능을 허용하시겠습니까?")
                this.setPositiveButton("이동") { dialog, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivityForResult(intent, 0)
                    dialog.dismiss()
                }
                this.setNegativeButton("종료"){ dialog, _ ->
                    dialog.cancel()
                    finish()
                }
            }
            alertDialog.show()
        }else{
            if (Build.VERSION.SDK_INT >= 23) {
                checkPermission()
            }

            val supportMapFragment: SupportMapFragment =
                supportFragmentManager.findFragmentById(R.id.GoogleMap) as SupportMapFragment
            supportMapFragment.getMapAsync(this)

            currentLocated()
            setAddressTargetLocation()
        }
    }

    private fun checkPermission() {
        val hasCoarseLocation: Int = ContextCompat.checkSelfPermission(this, permissions[0])
        val hasFineLocation: Int = ContextCompat.checkSelfPermission(this, permissions[1])

        if (hasCoarseLocation == PackageManager.PERMISSION_GRANTED && hasFineLocation == PackageManager.PERMISSION_GRANTED)
            startLocationUpdate()
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
                startLocationUpdate()
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

        val defaultLatLng = LatLng(35.21776, 128.68998)
        this.setTargetLocation(defaultLatLng, "창원")
    }

    //최초 위치셋팅.
    private fun setTargetLocation(latLng: LatLng, address: String) {
        targetLocation = Location("Target")
        targetLocation!!.latitude = latLng.latitude
        targetLocation!!.longitude = latLng.longitude

        val targetLatLng = LatLng(targetLocation!!.latitude, targetLocation!!.longitude)

        googleMap!!.clear()

        val circleOptions = CircleOptions()
        circleOptions.center(targetLatLng).radius(100.0).fillColor(Color.argb(51, 0, 0, 255))
            .strokeColor(Color.argb(51, 0, 0, 255))
        googleMap!!.addCircle(circleOptions)

        val markerOptions = MarkerOptions()
        markerOptions.position(targetLatLng).title(address).snippet("목적지")
            .icon(BitmapDescriptorFactory.fromBitmap(setTargetMarkerIcon()))
        googleMap!!.addMarker(markerOptions)

        val cameraUpdate: CameraUpdate = CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f)
        googleMap!!.moveCamera(cameraUpdate)
    }

    //위치정보를 받는 콜백
    private fun locationCallback() = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult?) {
            val list: List<Location> = p0!!.locations

            if (list.isNotEmpty()) {
                location = list[list.size - 1]
                currentLatLng = LatLng(location!!.latitude, location!!.longitude)
                currentLocation = location
                val distance = currentLocation!!.distanceTo(targetLocation)
                if (distance <= 100) {
                    val intent = Intent()
                    intent.action = "Near"
                    sendBroadcast(intent)
                } else {
                    val intent = Intent()
                    intent.action = "Far"
                    sendBroadcast(intent)
                }

                setCurrentLocation(currentLocation)
            }
        }
    }

    //현재위치 셋(최초(1회에 한하여)의경우에는 카메라 이동)
    private fun setCurrentLocation(location: Location?) {
        if (currentMarker != null)
            currentMarker!!.remove()

        val mCurrentLatLng = LatLng(location!!.latitude, location.longitude)
        val options = MarkerOptions().position(mCurrentLatLng)
            .icon(BitmapDescriptorFactory.fromBitmap(getCurrentMarkerIcon()))

        currentMarker = googleMap!!.addMarker(options)

        if (!first) {
            val cameraUpdate: CameraUpdate = CameraUpdateFactory.newLatLngZoom(mCurrentLatLng, 15f)
            googleMap!!.moveCamera(cameraUpdate)
            first = true
        }
    }

    //현재위치 아이콘
    private fun getCurrentMarkerIcon(): Bitmap {

        val bitmapDrawable = resources.getDrawable(R.drawable.ic_adjust_red_24dp, null)

        var bitmap: Bitmap = bitmapDrawable.toBitmap()
        bitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false)

        return bitmap
    }

    //설정위치 아이콘
    private fun setTargetMarkerIcon(): Bitmap {
        val bitmapDrawable = resources.getDrawable(R.drawable.ic_details_red_24dp, null)

        var bitmap: Bitmap = bitmapDrawable.toBitmap()
        bitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false)

        return bitmap

    }

    //업데이트 설정
    private fun startLocationUpdate() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback(),
            Looper.myLooper()
        )
    }

    //업데이트 해제(경보 리시버 해제)
    override fun onPause() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            fusedLocationClient.removeLocationUpdates(locationCallback())
        unregisterReceiver(warning)
        super.onPause()
    }

    //업데이트 등록(경보 리시버 등록)
    override fun onResume() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback(), null)

        warning = Warning()
        val intentFilter = IntentFilter("Near")
        intentFilter.addAction("Far")
        registerReceiver(warning, intentFilter)
        super.onResume()
    }

    //현재위치가져오는 버튼눌렀을 시 현재위치 가져오기
    private fun currentLocated() {
        CurrentLocate.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    first = false
                    if (currentLocation != null)
                        setCurrentLocation(currentLocation)
                }
            }
            true
        }
    }

    //목적위치 설정
    private fun setAddressTargetLocation() {
        setTargetLocation.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    createDialogTargetLocation()
                }
            }
            true
        }
    }

    //목적위치 설정 다이얼로그
    private fun createDialogTargetLocation() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("목적지를 입력해주세요.")
        val edit = EditText(this)
        alertDialog.setView(edit)
        alertDialog.setNegativeButton("취소"){ dialog, _ ->
            dialog.cancel()
        }
        alertDialog.setPositiveButton("설정"){ dialog, _ ->
            val findLocation = findAddressToLocation(edit.text.toString())
            val findLocationLatLng = LatLng(findLocation.latitude, findLocation.longitude)
            Log.e("findLocation : ", findLocationLatLng.toString())
            setTargetLocation(findLocationLatLng, edit.text.toString())
            dialog.dismiss()
        }
        if (dialog == null)
            dialog = alertDialog.create()
        dialog!!.show()
    }

    //해당 주소중 현재위치와 가장 가까운것을 가져옴
    private fun findAddressToLocation(address: String): Location {
        val location = Location("")
        val geoCoder = Geocoder(this)
        var list: List<Address>? = null
        var min = 0.0f
        var pos = 0

        try {
            list = geoCoder.getFromLocationName(address, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list!!.isNotEmpty()) {
            for(i in list.indices){
                Log.e("Address : ", list[i].adminArea)
                location.latitude = list[i].latitude
                location.longitude = list[i].longitude

                val distance = currentLocation!!.distanceTo(location)

                if(i == 0) {
                    min = distance
                }
                else if(distance < min) {
                    min = distance
                    pos = i
                }
            }

            location.longitude = list[pos].longitude
            location.latitude = list[pos].latitude
        }
        return location
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0){
            if(resultCode == Activity.RESULT_OK){
                if (Build.VERSION.SDK_INT >= 23) {
                    checkPermission()
                }
                currentLocated()
                setAddressTargetLocation()
            }else{
                finish()
            }
        }
    }
}
