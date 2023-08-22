package phycastle2.arduinogather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import phycastle2.arduinogather.ui.theme.ArduinoGatherTheme

class MainActivity : ComponentActivity() {

    private val REQUEST_PERMISSIONS = 123

    var serviceIntent : Intent? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val items = arrayOf("00:22:04:01:28:F6", "00:22:04:01:29:59")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        val spinner: Spinner = findViewById(R.id.main_spinner)
        spinner.adapter = adapter

        val sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val selectedValue = sharedPreferences.getString("mac","")

        val selectedIndex = items.indexOf(selectedValue)
        if (selectedIndex != -1) {
            spinner.setSelection(selectedIndex)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "arduino",
                "arduino",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }


        var btnStart =findViewById<Button>(R.id.main_btn_start)

        btnStart.setOnClickListener {
            val serviceIntent = Intent(this, BluetoothService::class.java)
            stopService(serviceIntent)

            val sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString("mac", spinner.selectedItem as String)
            editor.apply()

            Toast.makeText(this@MainActivity, "서비스가 시작되었습니다.", Toast.LENGTH_SHORT).show()
            this.serviceIntent = Intent(this, BluetoothService::class.java).apply {
                putExtra("DEVICE_ADDRESS", spinner.selectedItem as String)
            }
            startService(this.serviceIntent)
        }
        var btnStop =findViewById<Button>(R.id.main_btn_stop)

        btnStop.setOnClickListener {
            Toast.makeText(this@MainActivity, "서비스가 종료되었습니다.", Toast.LENGTH_SHORT).show()

            val serviceIntent = Intent(this, BluetoothService::class.java)
            stopService(serviceIntent)
        }


        checkPermissions()
    }

    private val updateUIReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message")
            var tvLog =findViewById<TextView>(R.id.main_tv_log)

            tvLog.text = message + tvLog.text
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(updateUIReceiver, IntentFilter("UPDATE_UI_ACTION"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateUIReceiver)
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {

                } else {
                    checkPermissions()
                }
                return
            }
        }
    }


}

