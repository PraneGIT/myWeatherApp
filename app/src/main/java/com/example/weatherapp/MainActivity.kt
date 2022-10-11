package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.location.LocationRequest;
import android.location.Location
import android.location.LocationManager
//import android.location.LocationRequest
import android.net.Uri
import android.net.Uri.fromParts
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import models.weatherResponse
import network.weatherService
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mSharedPreferences: SharedPreferences
    private var customProgressDialog:Dialog?=null
    private lateinit var mFusedLocationClient:FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        mFusedLocationClient=LocationServices.getFusedLocationProviderClient((this))

        if(!isLocationEnabled()){
            Toast.makeText(this, "turn on GPS", Toast.LENGTH_SHORT).show()
            val intent=Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object:MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "you've denied location", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }

                }).onSameThread()
                .check()
        }
    }

    private val mLocationCallback=object:LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? =locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("current latitude","$latitude")
            val longitude = mLastLocation?.longitude
            Log.i("current latitude","$longitude")
            if (longitude != null) {
                if (latitude != null) {
                    getLocationWeatherDetails(latitude,longitude)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest= LocationRequest()
        mLocationRequest.priority=LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback, Looper.myLooper()
        )
    }

    private fun isLocationEnabled():Boolean{
        val locationManager: LocationManager=
                    getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("you've turned off permissions")
            .setPositiveButton("GO to settings")
            { _,_->
                try {
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri= Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("cancel"){
                dialog,_->dialog.dismiss()
            }.show()
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
        val service:weatherService=retrofit.create<weatherService>(weatherService::class.java)

        val listCall: Call<weatherResponse> = service.getWeather(latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID)
            showProgressDialog()
        listCall.enqueue(object : Callback<weatherResponse> {
            override fun onResponse(
                call: Call<weatherResponse>,
                response: Response<weatherResponse>
            ) {
                cancelProgressDialog()
                if(response!!.isSuccessful){
                    val weatherList: weatherResponse? =response.body()
                    if (weatherList != null) {

                        setupUI(weatherList)
                    }
                    Log.i("response result","${weatherList}")
                }else{
                    val rc=response.code()
                    when(rc){
                        400->{
                            Log.e("error 400","not found")
                        }
                        else -> {
                            Log.e("error","generic error")
                        }
                    }
                }
            }

            override fun onFailure(call: Call<weatherResponse>, t: Throwable) {
                cancelProgressDialog()
                Log.e("errorrrr", t.message.toString())
            }

        })


        }else{
            Toast.makeText(this@MainActivity, "no internet connection", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showProgressDialog(){
        customProgressDialog= Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if(customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog=null
        }
    }

    private fun setupUI(weatherList: weatherResponse){
        val tvmain:TextView=findViewById(R.id.tv_main)
        val tvmaindescription:TextView=findViewById(R.id.tv_main_description)
        val tvtemp:TextView=findViewById(R.id.tv_temp)
        val tvhumidity:TextView=findViewById(R.id.tv_humidity)
        val tvMin:TextView=findViewById(R.id.tv_min)
        val tvMax:TextView=findViewById(R.id.tv_max)
        val tvSpeed:TextView=findViewById(R.id.tv_speed)
        val name:TextView=findViewById(R.id.tv_name)
        val country:TextView=findViewById(R.id.tv_country)
        val sunrisetime:TextView=findViewById(R.id.tv_sunrise)
        val sunset:TextView=findViewById(R.id.tv_sunset)
        val ivMain:ImageView=findViewById(R.id.iv_main)
        for(i in weatherList.weather.indices){
            Log.i("weather name",weatherList.weather.toString())
            tvmain.text=weatherList.weather[i].main
            tvmaindescription.text=weatherList.weather[i].description
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tvtemp.text=weatherList.main.temp.toString()+getUnit(application.resources.configuration.locales.toString())
            }
            tvhumidity.text=weatherList.main.humidity.toString()+" g/kg"
            tvMin.text=(weatherList.main.temp_min.toInt()-3).toString()+"°C"
            tvMax.text=(weatherList.main.temp_max.toInt()+3).toString()+"°C"
            val speed_num=weatherList.wind.speed.toFloat()*1.6
            val v=DecimalFormat("#.##")
            tvSpeed.text=(v.format(speed_num)).toString()
            name.text=weatherList.name
            country.text=weatherList.sys.country

            sunrisetime.text=unixTime(weatherList.sys.sunrise)+" AM"
            sunset.text=unixTime(weatherList.sys.sunset)+" PM"

             when(weatherList.weather[i].icon){
                 "01d"->ivMain.setImageResource(R.drawable.sunny)

                 "01d" -> ivMain.setImageResource(R.drawable.sunny)
                 "02d" -> ivMain.setImageResource(R.drawable.cloud)
                 "03d" -> ivMain.setImageResource(R.drawable.cloud)
                 "04d" -> ivMain.setImageResource(R.drawable.cloud)
                 "04n" -> ivMain.setImageResource(R.drawable.cloud)
                 "10d" -> ivMain.setImageResource(R.drawable.rain)
                 "11d" -> ivMain.setImageResource(R.drawable.storm)
                 "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                 "01n" -> ivMain.setImageResource(R.drawable.cloud)
                 "02n" -> ivMain.setImageResource(R.drawable.cloud)
                 "03n" -> ivMain.setImageResource(R.drawable.cloud)
                 "10n" -> ivMain.setImageResource(R.drawable.cloud)
                 "11n" -> ivMain.setImageResource(R.drawable.rain)
                 "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                 "50n" ->ivMain.setImageResource(R.drawable.cloud)
             }
        }

    }

    private fun getUnit(value: String): String? {
        var valu="°C"
        if(value=="US"){
            valu="°F"
        }
        return valu
    }
    private fun unixTime(timex:Long):String?{
        val date=Date(timex *1000L)
        val sdf= SimpleDateFormat("HH:mm")
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }
            else->return super.onOptionsItemSelected(item)
        }

    }


}