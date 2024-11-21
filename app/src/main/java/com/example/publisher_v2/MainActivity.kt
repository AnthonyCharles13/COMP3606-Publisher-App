package com.example.publisher_v2

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class MainActivity : AppCompatActivity(){
    private var client: Mqtt5BlockingClient? = null

//    private lateinit var tvlat : TextView
//    private lateinit var tvlong : TextView
//    private lateinit var tvspeed : TextView
//    private lateinit var tvstuid : TextView
//    private lateinit var tvtime : TextView
    private lateinit var et : EditText

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isPublishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }

//        tvlong = findViewById(R.id.longitudetv)
//        tvlat = findViewById(R.id.latitudetv)
//        tvspeed = findViewById(R.id.speedtv)
//        tvstuid = findViewById(R.id.stuid)
//        tvtime = findViewById(R.id.timetv)
        et = findViewById(R.id.editText)

        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816029229.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.disconnect()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val speed = location.speed * 3.6
                    val timestamp : Long = location.time

                    val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(timestamp))

//                    tvlat.text = latitude.toString()
//                    tvlong.text = longitude.toString()
//                    tvspeed.text = String.format("%.4f km/h", speed)
//                    tvtime.text = dateTime
//                    tvstuid.text = et.text

                    // Log time for debugging
                    //Log.d("LocationUpdate", "Time: $dateTime, Lat: $latitude, Lon: $longitude, Speed: $speed Km/h")



                    // Publish data to MQTT if publishing is enabled
                    if (isPublishing) {
                        publishLocation(et.text.toString(), latitude, longitude, speed.toFloat(), dateTime)
                    }
                }
            }
        }
    }

    fun startLocationUpdates(view: View?) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateIntervalMillis(4000) // Fastest update interval
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest.build(), locationCallback, Looper.getMainLooper())
        }

        try {
            client?.connect()
            isPublishing = true
        } catch (e:Exception){
            e.printStackTrace()
            Toast.makeText(this,"An error occurred when connecting to broker", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopLocationUpdates(view: View?) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isPublishing = false
        //tvstuid.text = "Student ID: 000000000"
        try {
            client?.disconnect()
        } catch (e:Exception){
           Toast.makeText(this,"An error occurred when disconnecting from broker", Toast.LENGTH_SHORT).show()
        }
        Toast.makeText(this, "Stopped location updates", Toast.LENGTH_SHORT).show()
    }

    private fun publishLocation(studentId: String, latitude: Double, longitude: Double, speed: Float, dateTime: String) {
        val locationData = "{\"StudentID\":$studentId,\"latitude\":$latitude,\"longitude\":$longitude,\"speed\":$speed km/h,\"time\":\"$dateTime\"}"

        val ld = LocationData(
            stuid = studentId,
            latitude = latitude,
            longitude = longitude,
            speed = speed,
            dateTime = dateTime
        )

        val jsonData = serializeLocationData(ld)

        try {
            Log.d("MQTT", "Publishing: $locationData")
            Log.d("IOT-MQTT", "Published: $jsonData")
            client?.publishWith()?.topic("assignment/location")?.payload(jsonData.toByteArray())?.send()
        } catch (e: Exception) {
            Log.e("MQTT", "Error publishing: ${e.message}")
            Toast.makeText(this, "Error publishing to MQTT", Toast.LENGTH_SHORT).show()
        }
    }

    private fun serializeLocationData(locationData: LocationData): String {
        val gson = com.google.gson.Gson()
        return gson.toJson(locationData)
    }

}