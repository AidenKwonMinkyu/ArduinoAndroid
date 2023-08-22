package phycastle2.arduinogather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothService : Service() {
    private val NOTIFICATION_ID = 1
    private val HC06_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private lateinit var readThread: Thread
    var deviceAddress = "00:22:04:01:28:F6"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var lastLat = 0.0
    private var lastLng = 0.0

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        deviceAddress = intent?.getStringExtra("DEVICE_ADDRESS").toString()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.locations?.forEach { location: Location ->

                    lastLat =location.latitude
                    lastLng = location.longitude
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        startForeground(NOTIFICATION_ID, createNotification())
        connectArduino()
        return START_NOT_STICKY
    }




    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, FLAG_MUTABLE)
        return Notification.Builder(this, "arduino")
            .setContentTitle("데이터 수집 중")
            .setContentText("데이터 수집 중...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun sendBroadcastToUpdateUI(message: String) {
        val intent = Intent("UPDATE_UI_ACTION")
        intent.putExtra("message", message)
        sendBroadcast(intent)
    }

    @SuppressLint("MissingPermission")
    private fun connectArduino() {

        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        sendBroadcastToUpdateUI("****연결 시도**** : "+deviceAddress+"\n")
        try {
            bluetoothSocket = device?.createRfcommSocketToServiceRecord(HC06_UUID)
        } catch (e: IOException) {
            Log.e("BluetoothApp", "Socket's create() method failed", e)
            sendBroadcastToUpdateUI("소켓 생성 실패\n")
        }

        bluetoothAdapter?.cancelDiscovery()

        try {
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            startReading()
            Log.d("BluetoothApp", "Connected to HC-06!")
            sendBroadcastToUpdateUI("****연결 성공**** : "+deviceAddress+"\n")
        } catch (connectException: IOException) {
            try {
                bluetoothSocket?.close()
                Log.e("BluetoothApp", "Cannot close the socket", connectException)
                sendBroadcastToUpdateUI("****연결 실패****\n")
            } catch (closeException: IOException) {
                Log.e("BluetoothApp", "Cannot close the socket", closeException)
                sendBroadcastToUpdateUI("****연결 실패****\n")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startReading() {
        readThread = Thread {
            try {
                val buffer = ByteArray(1024)
                var bytes: Int
                while (true) {
                    try {
                        var inputStream = inputStream
                        bytes = inputStream?.read(buffer) ?: -1
                        if (bytes > 0) {
                            val readMessage = String(buffer, 0, bytes)
                            Log.d("BluetoothApp", "Received: $readMessage")


                            val values = readMessage.split(",").map { it.trim().toFloat() }
                            val gatherReq = GatherReq(
                                mac = deviceAddress,
                                temperature = values[0],
                                humidity = values[1],
                                bmpTemperature = values[2],
                                bmpPressure = values[3],
                                bmpAltitude = values[4],
                                concentration = values[5],
                                ugm3 = values[6],
                                lat = lastLat,
                                lng = lastLng,
                            )


                            sendBroadcastToUpdateUI("서버 전송 : " +readMessage+","+lastLat+","+lastLng)

                            RetrofitClient.instance.postData(gatherReq).enqueue(object :
                                Callback<GatherRes> {
                                override fun onResponse(call: Call<GatherRes>, response: Response<GatherRes>) {
                                    if (response.isSuccessful) {
                                        sendBroadcastToUpdateUI("****전송 완료****\n")
                                    } else {
                                        sendBroadcastToUpdateUI("****전송 실패****\n")
                                    }
                                }

                                override fun onFailure(call: Call<GatherRes>, t: Throwable) {
                                    sendBroadcastToUpdateUI("****전송 실패****\n")
                                }
                            })

                        }
                    } catch (e: IOException) {
                        Log.e("BluetoothApp", "Error reading", e)
                        sendBroadcastToUpdateUI("****읽기 실패****\n")
                        break
                    }
                }
            } catch (ex: Exception) {
                Log.e("BluetoothApp", "Error in reading loop", ex)
                sendBroadcastToUpdateUI("****읽기 실패****\n")
            }
        }
        readThread.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()

        } catch (e: IOException) {
            Log.e("BluetoothApp", "Cannot close the socket", e)
        }
        try{
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }catch (e: Exception){

        }
    }
}