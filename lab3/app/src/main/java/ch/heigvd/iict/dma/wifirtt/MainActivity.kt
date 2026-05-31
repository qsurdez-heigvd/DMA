package ch.heigvd.iict.dma.wifirtt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import ch.heigvd.iict.dma.wifirtt.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Timer
import kotlin.concurrent.timer
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val wifiRttViewModel: WifiRttViewModel by viewModels()

    private lateinit var wifiManager: WifiManager
    private lateinit var wifiRttManager: WifiRttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // depuis android 15 (sdk 35), le mode edge2edge doit être activé
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // comme edge2edge est activé, l'application doit garder un espace suffisant pour la barre système
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // la barre d'action doit être définie dans le layout, on la lie à l'activité
        setSupportActionBar(binding.toolbar)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_list, R.id.navigation_map)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // 1. we request necessary permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestWifiRTTPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            )
        } else {
            requestWifiRTTPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }

        // 2.  check if Wifi RTT is available (when permissions are set)
        wifiRttViewModel.wifiRttPermissionsGranted.observe(this) { granted ->
            if (granted == null) return@observe
            if (granted && packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
                wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                wifiRttManager =
                    getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as WifiRttManager

                if (wifiRttManager.isAvailable)
                    wifiRttViewModel.wifiRttEnabledUpdate(true)
                else
                    Toast.makeText(
                        this@MainActivity,
                        R.string.wifi_rtt_disabled,
                        Toast.LENGTH_SHORT
                    ).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.wifi_rtt_unavailable, Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }

    private var rangingTask: Timer? = null

    override fun onStart() {
        super.onStart()
        // 3. we start ranging
        wifiRttViewModel.wifiRttEnabled.observe(this) { isEnabled ->
            if (isEnabled == null) return@observe
            if (isEnabled) {
                rangingTask?.cancel() // we cancel eventual previous task
                rangingTask =
                    timer("ranging_timer", daemon = false, initialDelay = 500, period = 250) {
                        val fineLocationGranted =
                            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val nearbyDevicesGranted =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
                            else true

                        if (!fineLocationGranted || !nearbyDevicesGranted) {
                            Log.w(TAG, "Permissions not granted")
                            return@timer
                        }


                        val scanResults = wifiManager.scanResults

                        val responders = scanResults.filter { it.is80211mcResponder }
                        if (responders.isEmpty()) return@timer

                        val request = RangingRequest.Builder().apply {
                            responders.forEach { addAccessPoint(it) }
                        }.build()
                        Log.d(TAG, "Responders: $responders")

                        wifiRttManager.startRanging(
                            request,
                            mainExecutor,
                            object : RangingResultCallback() {
                                override fun onRangingFailure(p0: Int) {
                                    Log.e(TAG, "Ranging failed")
                                }

                                override fun onRangingResults(p0: List<RangingResult>) {
                                    Log.d(TAG, "List received: $p0")
                                    wifiRttViewModel.onNewRangingResults(p0)
                                }
                            })
                    }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        rangingTask?.cancel()
    }

    private val requestWifiRTTPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            val isWifiRttPermissionGranted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) &&
                            permissions.getOrDefault(Manifest.permission.NEARBY_WIFI_DEVICES, false)
                else
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)

            wifiRttViewModel.wifiRttPermissionsGrantedUpdate(isWifiRttPermissionGranted)
        }

    companion object {
        private val TAG = MainActivity::class.simpleName
    }

}